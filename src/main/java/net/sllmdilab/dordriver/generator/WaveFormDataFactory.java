package net.sllmdilab.dordriver.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WaveFormDataFactory {

	public WaveFormData createRandomWaveFormData(WaveFormType type,
			Double sampleRate, long sampleTime, Double pulseRate,
			WaveFormModel model) {
		WaveFormData wf = new WaveFormData(type);
		List<Double> samples = createSamples(sampleRate, sampleTime, pulseRate,
				model);
		wf.setSamples(samples);
		wf.setRangeLow(Collections.min(samples));
		wf.setRangeHigh(Collections.max(samples));
		wf.setRate(sampleRate);
		return wf;
	}

	private List<Double> createSamples(Double sampleRate, long sampleTime,
			Double pulseRate, WaveFormModel model) {
		Double pulseInHz = pulseRate / 60; // pulseRate is in BPM.
		List<Double> samples = new ArrayList<>();
		Linspace counter = new Linspace(0.0f, 1.0 * sampleTime / 1000.0, sampleRate
				* sampleTime / 1000.0);

		double w = 2 * Math.PI * pulseInHz;
		double y;
		while (counter.hasNext()) {
			double x = counter.getNextDouble();
			switch (model) {
			case SIN:
				y = Math.sin(w * x);
				break;
			case LINEAR:
				y = x;
				break;
			default:
				y = x;
				break;
			}
			samples.add(y);
		}
		return samples;
	};

	private class Linspace {
		private double current;
		private final double end;
		private final double step;

		public Linspace(double start, double end, double totalCount) {
			this.current = start;
			this.end = end;
			this.step = (end - start) / totalCount;
		}

		public boolean hasNext() {
			return current < (end + step / 2);
		}

		public double getNextDouble() {
			double tmp = current;
			current += step;
			return tmp;
		}
	}
}

enum WaveFormModel {
	SIN, LINEAR
}
