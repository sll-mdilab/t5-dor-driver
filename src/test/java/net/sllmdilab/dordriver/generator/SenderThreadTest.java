package net.sllmdilab.dordriver.generator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import net.sllmdilab.dordriver.application.SenderThread;
import net.sllmdilab.dordriver.application.SenderThreadResult;
import net.sllmdilab.dordriver.network.Hl7Client;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.util.Terser;

@RunWith(MockitoJUnitRunner.class)
public class SenderThreadTest {

	//@formatter:off
	private static final String MOCK_MESSAGE ="MSH|^~\\&|SendingApplication|SendingFacility|ReceivingApplication|ReceivingFacility|20120530112345||ORU^R01^ORU_R01|9879790004|P|2.6|||NE|AL|USA|ASCII|EN^English^ISO639||IHE_PCD_ORU_R01^IHE PCD^1.3.6.1.4.1.19376.1.6.1.1.1^ISO\r"+
			"PID|||010101-2425||Doe^John^^^^^B\r"+
			"PV1||I|ICU^2^23\r"+
			"OBR|2||XXX|WAVEFORM|||20150615145531.000|20150615145532.000\r"+
			"OBX|1000||69965^MDC_DEV_MON_PHYSIO_MULTI_PARAM_MDS^MDC|1.0.0.0||||F||||||||||ABC123\r"+
			"OBX|1001|NA|131329^MDC_ECG_LEAD_I^MDC|1.1.1.1001|0.0^0.029039286658642516|266418^MDC_DIM_MILLI_VOLT^MDC||||||||20150615145531.000\r"+
			"OBX|1002|NM|0^MDC_ATTR_SAMP_RATE^MDC|1.1.1.1001.1|256.0|264608^MDC_DIM_PER_SEC\r"+
			"OBX|1003|NR|0^MDC_ATTR_DATA_RANGE^MDC|1.1.1.1001.2|-0.9999728924443673^0.9999969880372782||\r"+
			"OBX|1005|NA|131330^MDC_ECG_LEAD_II^MDC|1.1.1.1002|0.0^0.029039286658642516|266418^MDC_DIM_MILLI_VOLT^MDC||||||||20150615145531.000\r"+
			"OBX|1006|NM|0^MDC_ATTR_SAMP_RATE^MDC|1.1.1.1002.1|256.0|264608^MDC_DIM_PER_SEC\r"+
			"OBX|1007|NR|0^MDC_ATTR_DATA_RANGE^MDC|1.1.1.1002.2|-0.9999728924443673^0.9999969880372782||\r"+
			"OBX|1009|NA|131331^MDC_ECG_LEAD_III^MDC|1.1.1.1003|0.0^0.029039286658642516|266418^MDC_DIM_MILLI_VOLT^MDC||||||||20150615145531.000\r"+
			"OBX|1010|NM|0^MDC_ATTR_SAMP_RATE^MDC|1.1.1.1003.1|256.0|264608^MDC_DIM_PER_SEC\r"+
			"OBX|1011|NR|0^MDC_ATTR_DATA_RANGE^MDC|1.1.1.1003.2|-0.9999728924443673^0.9999969880372782||\r"+
			"OBR|3||XXX|1234^CONTINUOUS_WAVEFORM^YYY|||20150615145531.000|20150615145532.000\r"+
			"OBX|1013||69965^MDC_DEV_MON_PHYSIO_MULTI_PARAM_MDS^MDC|1.0.0.0||||F||||||||||ABC123\r"+
			"OBX|1014|NA|150456^MDC_PULS_OXIM_SAT_O2_WAVEFORM^MDC|1.1.1.1004|0.0^0.029039286658642516|262688^MDC_DIM_PERCENT^MDC||||||||20150615145531.000\r"+
			"OBX|1015|NM|0^MDC_ATTR_SAMP_RATE^MDC|1.1.1.1004.1|256.0|264608^MDC_DIM_PER_SEC\r"+
			"OBX|1016|NR|0^MDC_ATTR_DATA_RANGE^MDC|1.1.1.1004.2|-0.9999728924443673^0.9999969880372782||\r";
	//@formatter:on

	private SenderThreadResult senderThreadResult = new SenderThreadResult();

	@Mock
	Hl7Client hl7Client;

	@InjectMocks
	SenderThread senderThread = new SenderThread("localhost", 8870, Arrays.asList(MOCK_MESSAGE), 1, 0,
			senderThreadResult);

	@Before
	public void init() throws Exception {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void sendMessage() throws Exception {
		senderThread.run();
		senderThread.join();

		ArgumentCaptor<Message> messageArgumentCaptor = ArgumentCaptor.forClass(Message.class);

		verify(hl7Client).sendMessage(messageArgumentCaptor.capture());

		Message messageArgument = messageArgumentCaptor.getAllValues().get(0);
		Terser t = new Terser(messageArgument);
		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss.SSSx");

		ZonedDateTime obrStart = ZonedDateTime.parse(t.get("/.OBR-7"), dateTimeFormatter);
		ZonedDateTime obrEnd = ZonedDateTime.parse(t.get("/.OBR-8"), dateTimeFormatter);
		ZonedDateTime obx = ZonedDateTime.parse(t.get("/.OBSERVATION(1)/OBX-14"), dateTimeFormatter);

		assertEquals(1, senderThreadResult.sentMessages);
		assertEquals(obrStart, obx);
		assertEquals(1000, Duration.between(obrStart, obrEnd).toMillis());
	}
}
