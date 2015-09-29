package net.sllmdilab.dordriver.application;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.sllmdilab.dordriver.exeptions.DorDriverException;
import net.sllmdilab.dordriver.network.Hl7Client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.model.v26.datatype.DTM;
import ca.uhn.hl7v2.model.v26.group.ORU_R01_OBSERVATION;
import ca.uhn.hl7v2.model.v26.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v26.group.ORU_R01_PATIENT_RESULT;
import ca.uhn.hl7v2.model.v26.message.ORU_R01;
import ca.uhn.hl7v2.model.v26.segment.OBR;
import ca.uhn.hl7v2.model.v26.segment.OBX;
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory;

public class SenderThread extends Thread {
	private Logger logger = LoggerFactory.getLogger(SenderThread.class);

	private List<String> messages;
	private int numMessages;
	private long millisDelay;
	private boolean keepOriginalTimestamp = false; // Default
	private int sentMessages;
	private SenderThreadResult result;
	private HapiContext hapiContext;
	private Hl7Client hl7Client;
	private ThreadPoolExecutor threadPoolExecutor;

	public SenderThread(String destAddress, int destPort, List<String> messages, int numMessages, long millisDelay, boolean keepOriginalTimestamp,
			SenderThreadResult result) {

		this.messages = messages;
		this.numMessages = numMessages;
		this.millisDelay = millisDelay;
		this.result = result;
		this.keepOriginalTimestamp = keepOriginalTimestamp;

		// We need to have separate threads with separate HAPI contexts in order
		// to make sure we have parallel connections. See
		// http://hl7api.sourceforge.net/base/apidocs/ca/uhn/hl7v2/HapiContext.html#newClient

		threadPoolExecutor = new ThreadPoolExecutor(0, 2, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

		CanonicalModelClassFactory canonicalModelClassFactory = new CanonicalModelClassFactory("2.6");
		hapiContext = new DefaultHapiContext(threadPoolExecutor);
		hapiContext.setModelClassFactory(canonicalModelClassFactory);

		hl7Client = new Hl7Client(hapiContext, destAddress, destPort, false);
	}

	public SenderThread(String destAddress, int destPort, List<String> messages, int numMessages, long millisDelay,
			SenderThreadResult result) {
		this(destAddress, destPort, messages, numMessages, millisDelay, false, result);
	}

	@Override
	public void run() {
		logger.debug("Connecting.");

		try {
			hl7Client.connect();
		} catch (Exception e) {
			logger.error("Exception when connecting. ", e);
			++result.failedConnections;
			return;
		}

		logger.debug("Sending messages.");

		try {
			sendMessages(hl7Client);
		} catch (Exception e) {
			logger.error("Exception when sending messages. ", e);
			++result.failedConnections;
			return;
		}

		logger.debug("Sending completed.");
		logger.debug("Sent " + sentMessages + " messages in " + result.totalRunTimeMillis + " milliseconds.");

		try {
			hapiContext.close();
			threadPoolExecutor.shutdown();
		} catch (IOException e) {
			logger.error("Exception on cleanup.", e);
		}
	}

	private ORU_R01 parseOruMessage(String message) throws HL7Exception {
		return (ORU_R01) hapiContext.getPipeParser().parse(message);
	}

	private void sendMessages(Hl7Client hl7Client) throws HL7Exception {
		long startTimeMillis = System.currentTimeMillis();

		ORU_R01 firstMessage = parseOruMessage(messages.get(0));
		ORU_R01 lastMessage = parseOruMessage(messages.get(messages.size() - 1));

		Duration messageDuration = getOBRTimeFromStartToEnd(firstMessage, lastMessage);

		Duration timestampDifference = getTimestampDifference(firstMessage);

		int messageIndex = 0;
		while (sentMessages < numMessages) {
			long startIterTimeMillis = System.currentTimeMillis();
			logger.debug("Sending message " + messageIndex + "...");
			ORU_R01 message = null;
			try {
				message = parseOruMessage(messages.get(messageIndex));

				injectTimestampsForMessage(message, timestampDifference);

				long startSendTimeMillis = System.currentTimeMillis();
				hl7Client.sendMessage(message);
				result.totalSendTimeMillis += (System.currentTimeMillis() - startSendTimeMillis);

			} catch (HL7Exception | LLPException | IOException e) {
				logger.error("Exception when sending message.", e);
				++result.failedMessages;
			}

			logger.debug("Message sent.");

			++sentMessages;
			++messageIndex;

			boolean isLastMessage = messageIndex >= messages.size();
			if (isLastMessage) {
				messageIndex = 0;
				// Message duration defined when sending waveform messages.
				if (messageDuration != null) {
					/*
					 * Waveforms need continuation between messages, i.e. end time of a message is the start time of
					 * next message, hence the fixed addition to the timestampDifference
					 */
					timestampDifference = timestampDifference.plusMillis(messageDuration.toMillis());
				} else {
					// Continuation not as important for parametric data, hence calculating a new one.
					timestampDifference = getTimestampDifference(firstMessage);
				}
			}
			long stopIterTimeMillis = System.currentTimeMillis();
			long iterDuration = stopIterTimeMillis - startIterTimeMillis;
			try {
				if (sentMessages < numMessages && millisDelay > 0) {
					Thread.sleep(millisDelay - iterDuration);
				}
			} catch (InterruptedException e) {
				throw new DorDriverException("Thread interrupted: ", e);
			}
		}

		result.totalRunTimeMillis = System.currentTimeMillis() - startTimeMillis;
		result.sentMessages = sentMessages;
		result.threadId = getId();
	}

	/**
	 * Calculates the OBR time covered by two messages. It is the duration between start time of firstMessage and
	 * endTime of lastMessage. Returns null if no endTime is found in lastMessage.
	 * 
	 * @param firstMessage
	 * @param lastMessage
	 * @return
	 */
	private Duration getOBRTimeFromStartToEnd(ORU_R01 firstMessage, ORU_R01 lastMessage) {
		DTM startDate = firstMessage.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR().getObr7_ObservationDateTime();
		DTM endDate = lastMessage.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR().getObr8_ObservationEndDateTime();

		try {
			if (endDate.isEmpty()) {
				return null;
			}
			Date start = startDate.getValueAsDate();
			Date end = endDate.getValueAsDate();
			return Duration.between(start.toInstant(), end.toInstant());
		} catch (HL7Exception e) {
			throw new DorDriverException("Error when calculating OBR time covered by two messages.", e);
		}
	}

	/**
	 * Fetch the difference between initial timestamp and current timestamp. This will later be appended to each
	 * subsequent timestamp in order to maintain consistent time between messages.
	 */
	private Duration getTimestampDifference(ORU_R01 message) throws HL7Exception {
		OBR obr = message.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR();

		// If end date is missing (not a waveform message), use start date
		// instead
		DTM date = obr.getObr8_ObservationEndDateTime();
		if (date.isEmpty()) {
			date = obr.getObr7_ObservationDateTime();
		}

		if (date.isEmpty()) {
			throw new DorDriverException("OBR missing end date.");
		} else {
			return Duration.between(date.getValueAsDate().toInstant(), new Date().toInstant());
		}
	}

	/**
	 * Add the specified timestamp difference to each relevant timestamp in a message, modifying the message.
	 */
	private void injectTimestampsForMessage(ORU_R01 message, Duration timestampDifference) throws HL7Exception {
		message.getMSH().getDateTimeOfMessage().setValue(new Date());
		if(!this.keepOriginalTimestamp){
			for (ORU_R01_PATIENT_RESULT patientResult : message.getPATIENT_RESULTAll()) {
				for (ORU_R01_ORDER_OBSERVATION orderObservation : patientResult.getORDER_OBSERVATIONAll()) {
					injectTimestampsForOrderObservation(timestampDifference, orderObservation);
				}
			}
		}
	}

	private void injectTimestampsForOrderObservation(Duration timestampDifference,
			ORU_R01_ORDER_OBSERVATION orderObservation) throws HL7Exception {

		injectTimestampsForObr(timestampDifference, orderObservation);

		for (ORU_R01_OBSERVATION observation : orderObservation.getOBSERVATIONAll()) {
			injectTimestampsForObx(timestampDifference, observation);
		}
	}

	private void injectTimestampsForObx(Duration timestampDifference, ORU_R01_OBSERVATION observation)
			throws HL7Exception {
		OBX obx = observation.getOBX();
		DTM start = obx.getDateTimeOfTheObservation();

		if (!start.isEmpty()) {
			start.setValue(plusOffset(start.getValueAsDate(), timestampDifference));
		}
	}

	private void injectTimestampsForObr(Duration timestampDifference, ORU_R01_ORDER_OBSERVATION orderObservation)
			throws HL7Exception {
		DTM orderStart = orderObservation.getOBR().getObr7_ObservationDateTime();
		if (!orderStart.isEmpty()) {
			orderStart.setValue(plusOffset(orderStart.getValueAsDate(), timestampDifference));
		}

		DTM orderEnd = orderObservation.getOBR().getObr8_ObservationEndDateTime();
		if (!orderEnd.isEmpty()) {
			orderEnd.setValue(plusOffset(orderEnd.getValueAsDate(), timestampDifference));
		}
	}

	private Date plusOffset(Date date, Duration offset) {
		ZonedDateTime zdt = ZonedDateTime.ofInstant(date.toInstant(), ZoneOffset.UTC);

		return Date.from(zdt.plus(offset).toInstant());
	}
}
