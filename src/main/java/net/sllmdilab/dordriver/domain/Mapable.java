package net.sllmdilab.dordriver.domain;

import java.util.Map;

public interface Mapable {

	/**
	 * Object representation as a Map
	 * @return
	 */
	public Map<String, String> toMap();
}
