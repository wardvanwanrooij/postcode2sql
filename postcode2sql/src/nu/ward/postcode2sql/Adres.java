package nu.ward.postcode2sql;

public class Adres {
	private String id;

	private String straat;

	private String plaats;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getStraat() {
		return straat;
	}

	public void setStraat(String straat) {
		this.straat = straat;
	}

	public String getPlaats() {
		return plaats;
	}

	public void setPlaats(String plaats) {
		this.plaats = plaats;
	}

	@Override
	public String toString() {
		return "Adres [id=" + id + ", straat=" + straat + ", plaats=" + plaats
				+ "]";
	}
}
