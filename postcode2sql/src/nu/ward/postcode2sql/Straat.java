package nu.ward.postcode2sql;

import java.util.List;

public class Straat {
	private String postcode;

	private String naam;

	private String plaats;

	private Integer huisnummerStart;

	private Integer huisnummerEinde;

	private boolean even;

	private boolean oneven;

	private List<Integer> listHuisnummer;

	public String getPostcode() {
		return postcode;
	}

	public void setPostcode(String postcode) {
		this.postcode = postcode;
	}

	public String getNaam() {
		return naam;
	}

	public void setNaam(String naam) {
		this.naam = naam;
	}

	public String getPlaats() {
		return plaats;
	}

	public void setPlaats(String plaats) {
		this.plaats = plaats;
	}

	public Integer getHuisnummerStart() {
		return huisnummerStart;
	}

	public void setHuisnummerStart(Integer huisnummerStart) {
		this.huisnummerStart = huisnummerStart;
	}

	public Integer getHuisnummerEinde() {
		return huisnummerEinde;
	}

	public void setHuisnummerEinde(Integer huisnummerEinde) {
		this.huisnummerEinde = huisnummerEinde;
	}

	public boolean isEven() {
		return even;
	}

	public void setEven(boolean even) {
		this.even = even;
	}

	public boolean isOneven() {
		return oneven;
	}

	public void setOneven(boolean oneven) {
		this.oneven = oneven;
	}

	public List<Integer> getListHuisnummer() {
		return listHuisnummer;
	}

	public void setListHuisnummer(List<Integer> listHuisnummer) {
		this.listHuisnummer = listHuisnummer;
	}

	@Override
	public String toString() {
		return "Straat [postcode=" + postcode + ", straat=" + naam
				+ ", plaats=" + plaats + ", huisnummerStart=" + huisnummerStart
				+ ", huisnummerEinde=" + huisnummerEinde + ", even=" + even
				+ ", oneven=" + oneven + ", listHuisnummer=" + listHuisnummer
				+ "]";
	}
}
