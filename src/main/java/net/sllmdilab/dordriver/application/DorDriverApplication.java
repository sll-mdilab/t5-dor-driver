package net.sllmdilab.dordriver.application;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sllmdilab.dordriver.exeptions.UnsupportedMessageTypeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.llp.LLPException;
import ca.uhn.hl7v2.model.v26.message.ORU_R01;
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Hl7InputStreamMessageStringIterator;

public class DorDriverApplication implements Runnable {
	private static Logger logger = LoggerFactory.getLogger(DorDriverApplication.class);

	private Integer numMessages = 1;
	private Long millisDelay = 0L;
	private String inputFileName;
	private InputStream inputStream;
	private Integer numThreads = 1;
	private String destAddress;
	private Integer destPort;
	private boolean keepOriginalTimestamp = false; // Default

	public static void main(String[] args) throws HL7Exception, LLPException, IOException, InterruptedException {
		(new DorDriverApplication()).readAndSendMessages(args);
	}

	public DorDriverApplication() {

	}

	public DorDriverApplication(String destAddress, Integer destPort, Integer numThreads, boolean keepOriginalTimestamp) {
		this(destAddress, destPort, null, null, null, numThreads, keepOriginalTimestamp);
	}

	public DorDriverApplication(String destAddress, Integer destPort, InputStream inputStream, Integer numMessages,
			Long millisDelay, Integer numThreads, boolean keepOriginalTimestamp) {
		this.destAddress = destAddress;
		this.destPort = destPort;
		this.inputStream = inputStream;
		this.numMessages = numMessages;
		this.millisDelay = millisDelay;
		this.numThreads = numThreads;
		this.keepOriginalTimestamp = keepOriginalTimestamp;
	}

	private List<String> readMessages(InputStream inputStream, long numMessages) throws IOException {
		Iterator<String> it = new Hl7InputStreamMessageStringIterator(inputStream);

		CanonicalModelClassFactory canonicalModelClassFactory = new CanonicalModelClassFactory("2.6");
		HapiContext hapiContext = new DefaultHapiContext(canonicalModelClassFactory);
		PipeParser parser = hapiContext.getPipeParser();

		List<String> messageStrings = new ArrayList<>();
		long messageIndex = 0;

		while (it.hasNext()) {
			try {
				logger.debug("Reading message " + messageIndex);
				String messageString = it.next();

				if (!(parser.parse(messageString) instanceof ORU_R01)) {
					throw new UnsupportedMessageTypeException("Unsupported message type.");
				}

				messageStrings.add(messageString);

			} catch (HL7Exception | UnsupportedMessageTypeException e) {
				logger.warn("Error parsing message " + messageIndex + ", skipping it: " + e);
			}
			++messageIndex;
		}

		return messageStrings;
	}

	private void parseArgs(String[] args) throws FileNotFoundException {

		if (args.length < 3) {
			printUsage();
			System.exit(-1);
		}
		destAddress = args[0];
		destPort = Integer.parseInt(args[1]);
		inputFileName = args[2];
		inputStream = new FileInputStream(inputFileName);

		if (args.length > 3) {
			numMessages = Integer.parseInt(args[3]);
		}

		if (args.length > 4) {
			millisDelay = Long.parseLong(args[4]);
		}

		if (args.length > 5) {
			numThreads = Integer.parseInt(args[5]);
		}
	}

	private void printUsage() {
		System.out
				.println("Usage: host port inputfile [number of messages] [delay in milliseconds] [number of threads]");
	}

	private void readAndSendMessages(String[] args) throws HL7Exception, LLPException, InterruptedException,
			IOException {
		parseArgs(args);
		readAndSendMessages();
	}

	private void readAndSendMessages() throws HL7Exception, LLPException, InterruptedException, IOException {
		List<String> messages = readMessages(inputStream, numMessages);

		logger.info("Read " + messages.size() + " HL7v2 messages from input stream.");
		logger.info("Starting threads.");

		List<SenderThreadResult> results = startAndWaitForThreads(numMessages, millisDelay, numThreads, destAddress,
				destPort, messages, keepOriginalTimestamp);

		logger.info("All threads completed.");

		writeResults(results);
	}

	private void writeResults(List<SenderThreadResult> results) {

		long sumMillis = 0;
		long sumSendMillis = 0;
		int sumSentMessages = 0;
		int sumFailedConnections = 0;

		for (SenderThreadResult result : results) {
			sumMillis += result.totalRunTimeMillis;
			sumSendMillis += result.totalSendTimeMillis;
			sumSentMessages += result.sentMessages;
			sumFailedConnections += result.failedConnections;

			logger.info("Thread " + result.threadId + " sent " + result.sentMessages + " messages in "
					+ result.totalRunTimeMillis + " milliseconds.");
		}

		long averageMillis = sumMillis / results.size();
		long averageSendMillis = sumSendMillis / results.size();

		System.out.println("### Total runtime(including delay): " + sumMillis);
		System.out.println("### Total runtime(sending only): " + sumSendMillis);
		System.out.println("### Average runtime per thread(including delay): " + averageMillis);
		System.out.println("### Average runtime per thread (sending only): " + averageSendMillis);
		System.out.println("### Successfully sent messages: " + sumSentMessages);
		System.out.println("### Failed connections: " + sumFailedConnections);
	}

	private List<SenderThreadResult> startAndWaitForThreads(int numMessages, long millisDelay, int numThreads,
			String destAddress, int destPort, List<String> messages, boolean keepOriginalTimestamp)
			throws InterruptedException {

		List<SenderThread> threads = new ArrayList<>();
		List<SenderThreadResult> results = new ArrayList<>();

		for (int i = 0; i < numThreads; ++i) {
			SenderThreadResult result = new SenderThreadResult();
			results.add(result);

			SenderThread thread = new SenderThread(destAddress, destPort, messages, numMessages, millisDelay, keepOriginalTimestamp, result);
			threads.add(thread);

			thread.start();
		}

		for (Thread thread : threads) {
			thread.join();
		}

		return results;
	}

	@Override
	public void run() {
		try {
			readAndSendMessages();
		} catch (HL7Exception | LLPException | InterruptedException | IOException e) {
			logger.error("Failed to run DorDriverException.", e);
		}
	}

	public void setNumMessages(Integer numMessages) {
		this.numMessages = numMessages;
	}

	public void setMillisDelay(Long millisDelay) {
		this.millisDelay = millisDelay;
	}

	public void setInputStream(InputStream inputStream) {
		this.inputStream = inputStream;
	}
}
