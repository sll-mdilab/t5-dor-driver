package net.sllmdilab.dordriver.generator;

import java.util.List;
import java.util.Map;

import net.sllmdilab.commons.util.T5FHIRUtils;
import net.sllmdilab.dordriver.domain.Mapable;

public abstract class AbstractWaveFormData implements Mapable {
	public static final String RANGE_HIGH_KEY = "RANGE_HIGH";
	public static final String RANGE_HIGH_PREFIX = RANGE_HIGH_KEY + "_";
	public static final String RANGE_LOW_KEY = "RANGE_LOW";
	public static final String RANGE_LOW_PREFIX = RANGE_LOW_KEY + "_";
	public static final String RATE_KEY = "RATE";
	public static final String RATE_PREFIX = RATE_KEY + "_";
	public static final String SAMPLES_KEY = "SAMPLES";
	public static final String SAMPLES_PREFIX = SAMPLES_KEY + "_";
	public static final String START_TIME_KEY = "START_TIME";
	public static final String END_TIME_KEY = "END_TIME";
	public static final String DATA_TYPE_KEY = "DATA_TYPE";

	private List<Double> samples;
	private double rangeLow;
	private double rangeHigh;
	private double rate;
	private String startTime;
	private String endTime;
	private WaveFormType type;

	private String sampleSeparator = "^";

	public AbstractWaveFormData() {

	}

	public AbstractWaveFormData(WaveFormType type) {
		this(null, .0, .0, .0, type, null, null);
	}

	public AbstractWaveFormData(List<Double> samples, double rangeLow, double rangeHigh, double rate, WaveFormType type) {
		this(samples, rangeLow, rangeHigh, rate, type, null, null);
	}

	public AbstractWaveFormData(List<Double> samples, double rangeLow, double rangeHigh, double rate,
			WaveFormType type, String startTime, String endTime) {
		this.samples = samples;
		this.rangeLow = rangeLow;
		this.rangeHigh = rangeHigh;
		this.rate = rate;
		this.type = type;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public List<Double> getSamples() {
		return samples;
	}

	public void setSamples(List<Double> samples) {
		this.samples = samples;
	}

	public double getRangeLow() {
		return rangeLow;
	}

	public void setRangeLow(double rangeLow) {
		this.rangeLow = rangeLow;
	}

	public double getRangeHigh() {
		return rangeHigh;
	}

	public void setRangeHigh(double rangeHigh) {
		this.rangeHigh = rangeHigh;
	}

	public double getRate() {
		return rate;
	}

	public void setRate(double rate) {
		this.rate = rate;
	}

	public WaveFormType getType() {
		return type;
	}

	public void setType(WaveFormType type) {
		this.type = type;
	}

	public String getStartTime() {
		return startTime;
	}

	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}

	public String getEndTime() {
		return endTime;
	}

	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}

	public String getSampleSeparator() {
		return sampleSeparator;
	}

	public void setSampleSeparator(String sampleSeparator) {
		this.sampleSeparator = sampleSeparator;
	}

	@Override
	public abstract Map<String, String> toMap();

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof AbstractWaveFormData)) {
			return false;
		}
		AbstractWaveFormData that = (AbstractWaveFormData) obj;
		boolean parametersEqual = this.rangeHigh == that.rangeHigh && this.rangeLow == that.rangeLow
				&& this.rate == that.rate && this.type == that.type;
		boolean timesEqual = T5FHIRUtils.convertHL7DateTypeToDate(this.startTime).equals(
				T5FHIRUtils.convertHL7DateTypeToDate(that.startTime))
				&& T5FHIRUtils.convertHL7DateTypeToDate(this.endTime).equals(
						T5FHIRUtils.convertHL7DateTypeToDate(that.endTime));
		boolean samplesEqual = this.samples.equals(that.samples);
		return parametersEqual && timesEqual && samplesEqual;

	}

}
