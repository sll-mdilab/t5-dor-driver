package net.sllmdilab.dordriver.generator;

public enum WaveFormType {
	ECG1("MDC_ECG_LEAD_I"), 
	ECG2("MDC_ECG_LEAD_II"), 
	ECG3("MDC_ECG_LEAD_III"), 
	SAT("MDC_PULS_OXIM_SAT_O2_WAVEFORM"), 
	ABP("MDC_PRESS_BLD_ART");
	
	private final String code;

	WaveFormType(String code) {
		this.code = code;
	}

	public String getCode() {
		return this.code;
	}
}
