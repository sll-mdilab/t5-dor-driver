package net.sllmdilab.dordriver.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

public class WaveFormData {
	private static final String RANGE_HIGH_PREFIX = "RANGE_HIGH_";
	private static final String RANGE_LOW_PREFIX = "RANGE_LOW_";
	private static final String RATE_PREFIX = "RATE_";
	private static final String SAMPLES_PREFIX = "SAMPLES_";
	private List<Double> samples;
	private double rangeLow;
	private double rangeHigh;
	private double rate;
	private WaveFormType type;

	private String sampleSeparator = "^";

	public WaveFormData(WaveFormType type) {
		this.type = type;
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

	public Map<String, String> toMap() {
		Map<String, String> map = new HashMap<>();
		map.put(SAMPLES_PREFIX + this.type,
				StringUtils.join(this.samples, sampleSeparator));
		map.put(RATE_PREFIX + this.type, Double.toString(this.rate));
		map.put(RANGE_LOW_PREFIX + this.type, Double.toString(this.rangeLow));
		map.put(RANGE_HIGH_PREFIX + this.type, Double.toString(this.rangeHigh));
		return map;
	}

}
