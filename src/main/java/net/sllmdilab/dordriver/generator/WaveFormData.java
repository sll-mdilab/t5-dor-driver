package net.sllmdilab.dordriver.generator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class WaveFormData extends AbstractWaveFormData {
	public WaveFormData() {

	}

	public WaveFormData(List<Double> samples, double rangeLow, double rangeHigh, double rate, WaveFormType type,
			String startTime, String endTime) {
		super(samples, rangeLow, rangeHigh, rate, type, startTime, endTime);
	}

	public WaveFormData(List<Double> samples, double rangeLow, double rangeHigh, double rate, WaveFormType type) {
		super(samples, rangeLow, rangeHigh, rate, type);
	}

	public WaveFormData(WaveFormType type) {
		super(type);
	}

	@Override
	public Map<String, String> toMap() {
		Map<String, String> map = new HashMap<>();
		map.put(SAMPLES_PREFIX + this.getType(), StringUtils.join(this.getSamples(), this.getSampleSeparator()));
		map.put(RATE_PREFIX + this.getType(), Double.toString(this.getRate()));
		map.put(RANGE_LOW_PREFIX + this.getType(), Double.toString(this.getRangeLow()));
		map.put(RANGE_HIGH_PREFIX + this.getType(), Double.toString(this.getRangeHigh()));
		map.put(START_TIME_KEY + this.getType(), this.getStartTime());
		map.put(END_TIME_KEY + this.getType(), this.getEndTime());
		return map;
	}
}
