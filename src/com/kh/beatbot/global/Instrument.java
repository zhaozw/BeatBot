package com.kh.beatbot.global;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Instrument {
	private String name;
	private BeatBotIconSource iconSource;
	private File[] sampleFiles;
	private String[] sampleNames;
	
	public Instrument(String instrumentName, BeatBotIconSource instrumentIcon) {
		this.name = instrumentName;
		this.iconSource = instrumentIcon;
		File dir = new File(GlobalVars.appDirectory + instrumentName);
		sampleFiles = dir.listFiles();
		sampleNames = dir.list();
	}
	
	public byte[] getSampleBytes(int sampleNum) {
		byte[] bytes = null;
		try {
			File sampleFile = sampleFiles[sampleNum];
			FileInputStream in = new FileInputStream(sampleFile);
			bytes = new byte[(int)sampleFile.length()];
			in.read(bytes);
			in.close();
			in = null;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bytes;
	}
	
	public String getName() {
		return name;
	}

	public BeatBotIconSource getIconSource() {
		return iconSource;
	}

	public void setIconResources(int defaultIconResourceId, int selectedIconResourceId, int listViewIconResourceId) {
		this.iconSource.set(defaultIconResourceId, selectedIconResourceId, listViewIconResourceId);
	}
	
	public File getSampleFile(int sampleNum) {
		return sampleFiles[sampleNum];
	}

	public String[] getSampleNames() {
		return sampleNames;
	}
	
	public String getSampleName(int sampleNum) {
		return sampleNames[sampleNum];
	}
}