package nu.ward.postcode2sql;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;

public class Converter {
	private static final String BEGINDATUMTIJDVAKGELDIGHEID = "begindatumTijdvakGeldigheid";
	private static final String EINDDATUMTIJDVAKGELDIGHEID = "einddatumTijdvakGeldigheid";
	private static final String GERELATEERDEOPENBARERUIMTE = "gerelateerdeOpenbareRuimte";
	private static final String GERELATEERDEWOONPLAATS = "gerelateerdeWoonplaats";
	private static final Object HUISNUMMER = "huisnummer";
	private static final String IDENTIFICATIE = "identificatie";
	private static final String NAAMGEVINGUITGEGEVEN = "Naamgeving uitgegeven";
	private static final String NUMMERAANDUIDING = "Nummeraanduiding";
	private static final String NUMMERAANDUIDINGSTATUS = "nummeraanduidingStatus";
	private static final String OPENBARERUIMTENAAM = "openbareRuimteNaam";
	private static final String OPENBARERUIMTE = "OpenbareRuimte";
	private static final Object POSTCODE = "postcode";
	private static final String URL = "https://github.com/wardvanwanrooij/postcode2sql";
	private static final String VERSION = "postcode2sql build 20201123";
	private static final String WOONPLAATS = "Woonplaats";
	private static final String WOONPLAATSNAAM = "woonplaatsNaam";
	private static final Logger logger = Logger.getLogger(Converter.class.getName());
	private final HashMap<String, HashMap<String, Straat>> NUM = new HashMap<String, HashMap<String, Straat>>();
	private final HashMap<String, Adres> OPR = new HashMap<String, Adres>();
	private final HashMap<String, String> WPL = new HashMap<String, String>();
	
	public boolean convert(String rootFile, String outputFile, String tableName) {
		String version;

		NUM.clear();
		OPR.clear();
		WPL.clear();
		if ((version = read(rootFile)) == null) return false;
		if (!fixup()) return false;
		if (!determineRange()) return false;
		if (!fillPOBox()) return false;
		if (!write(version, outputFile, tableName)) return false;
		return true;
	}

	private String read(String rootFile) {
		FileTime modTime = null;
		boolean res = true;

		logger.info("reading from " + rootFile);
		try (ZipFile zipFile = new ZipFile(rootFile)) {
			Enumeration<? extends ZipEntry> zipEntries;
			XMLInputFactory xmlif;

			xmlif = XMLInputFactory.newInstance();
			xmlif.setProperty(XMLInputFactory.IS_COALESCING, true);
			for (int phase = 0; phase < 3; phase++) {
				zipEntries = zipFile.entries();
				while (res && zipEntries.hasMoreElements()) {
					ZipEntry zipEntry;
	
					zipEntry = zipEntries.nextElement();
					if ((modTime == null) || (modTime.compareTo(zipEntry.getLastModifiedTime()) > 0)) {
						modTime = zipEntry.getLastModifiedTime();
					}
					if ((zipEntry.getName().length() >= 7) && (zipEntry.getName().matches("\\d{4}.*"))) {
						String id;
						
						id = zipEntry.getName().substring(4, 7);
						if (id.equals("WPL") && (phase == 0)) {
							res &= convert(xmlif, zipFile.getInputStream(zipEntry), this::parseWPL);
						} else if (id.equals("OPR") && (phase == 1)) {
							res &= convert(xmlif, zipFile.getInputStream(zipEntry), this::parseOPR);
						} else if (id.equals("NUM") && (phase == 2)) {
							res &= convert(xmlif, zipFile.getInputStream(zipEntry), this::parseNUM);
						}
					}
				}
			}
			zipFile.close();
			if (modTime == null) {
				return null;
			} else {
				return res ? modTime.toString() : null;
			}
		} catch (IOException e) {
		    logger.log(Level.SEVERE, "error extracting root zip file", e);
		    return null;
		}
	}

	private boolean convert(XMLInputFactory xmlif, InputStream zippedInputStream, Predicate<XMLStreamReader> function) {
		boolean res = true;

		try (ZipInputStream zis = new ZipInputStream(zippedInputStream)) {
			while (zis.getNextEntry() != null) {
				XMLStreamReader xmlsr;

				xmlsr = xmlif.createXMLStreamReader(new UncloseableInputStream(zis));
				res &= function.test(xmlsr);
				xmlsr.close();
				zis.closeEntry();
			}
			zis.close();
			return res;
		} catch (IOException e) {
		    logger.log(Level.SEVERE, "i/o error extracting embedded zip file", e);
		    return false;
		} catch (XMLStreamException e) {
		    logger.log(Level.SEVERE, "parse error extracting embedded zip file", e);
		    return false;
		}
	}

	private boolean parseWPL(XMLStreamReader xmlsr) {
		String id = null, name = null;
		boolean inRecord = false, inId = false, inName = false;

		try {
			while (xmlsr.hasNext()) {
				String localName = null;
				int eventCode;

				eventCode = xmlsr.next();
				if ((eventCode == XMLEvent.START_ELEMENT) || (eventCode == XMLEvent.END_ELEMENT)) {
					localName = xmlsr.getLocalName();
				}
				switch (eventCode) {
				case XMLEvent.START_ELEMENT:
					if (localName.equals(WOONPLAATS)) {
						id = null;
						name = null;
						inRecord = true;
					} else if (inRecord && localName.equals(IDENTIFICATIE)) {
						inId = true;
					} else if (inRecord && localName.equals(WOONPLAATSNAAM)) {
						inName = true;
					}
					break;
				case XMLEvent.CHARACTERS:
					if (inId) id = xmlsr.getText();
					if (inName) name = xmlsr.getText();
					break;
				case XMLEvent.END_ELEMENT:
					if (xmlsr.getLocalName().equals(WOONPLAATS)) {
						WPL.put(id, name);
						inRecord = false;
					} else if (inRecord && localName.equals(IDENTIFICATIE)) {
						inId = false;
					} else if (inRecord && localName.equals(WOONPLAATSNAAM)) {
						inName = false;
					}
					break;
				}
			}
			return true;
		} catch (XMLStreamException e) {
		    logger.log(Level.SEVERE, "parse error WPL", e);
		    return false;
		}
	}

	private boolean parseOPR(XMLStreamReader xmlsr) {
		String id = null, street = null, city = null;
		boolean inRecord = false, inId = false, inStreet = false, inRelated = false, inCity = false;

		try {
			while (xmlsr.hasNext()) {
				String localName = null;
				int eventCode;

				eventCode = xmlsr.next();
				if ((eventCode == XMLEvent.START_ELEMENT) || (eventCode == XMLEvent.END_ELEMENT)) {
					localName = xmlsr.getLocalName();
				}
				switch (eventCode) {
				case XMLEvent.START_ELEMENT:
					if (localName.equals(OPENBARERUIMTE)) {
						id = null;
						street = null;
						city = null;
						inRecord = true;
					} else if (inRecord && (!inRelated) && localName.equals(IDENTIFICATIE)) {
						inId = true;
					} else if (inRecord && localName.equals(OPENBARERUIMTENAAM)) {
						inStreet = true;
					} else if (inRecord && localName.equals(GERELATEERDEWOONPLAATS)) {
						inRelated = true;
					} else if (inRelated && localName.equals(IDENTIFICATIE)) {
						inCity = true;
					}
					break;
				case XMLEvent.CHARACTERS:
					if (inId) id = xmlsr.getText();
					if (inStreet) street = xmlsr.getText();
					if (inCity) city = xmlsr.getText();
					break;
				case XMLEvent.END_ELEMENT:
					if (localName.equals(OPENBARERUIMTE)) {
						Adres adres;

						adres = new Adres();
						adres.setId(id);
						adres.setStraat(street);
						adres.setPlaats(city);
						OPR.put(id, adres);
						inRecord = false;
					} else if (inRecord && (!inRelated)  && localName.equals(IDENTIFICATIE)) {
						inId = false;
					} else if (inRecord && localName.equals(OPENBARERUIMTENAAM)) {
						inStreet = false;
					} else if (inRecord && localName.equals(GERELATEERDEWOONPLAATS)) {
						inRelated = false;
					} else if (inRelated && localName.equals(IDENTIFICATIE)) {
						inCity = false;
					}
					break;
				}
			}
			return true;
		} catch (XMLStreamException e) {
		    logger.log(Level.SEVERE, "parse error OPR", e);
		    return false;
		}
	}

	private boolean parseNUM(XMLStreamReader xmlsr) {
		Integer endDate = null, number = null, startDate = null, today;
		String building = null, postcode = null, status = null;
		boolean inBuilding = false, inEndDate = false, inNumber = false, inPostcode = false, inRecord = false, inRelated = false, inStartDate = false, inStatus = false;

		try {
			today = Integer.valueOf(new SimpleDateFormat("yyyyMMdd").format(new Date()));
			while (xmlsr.hasNext()) {
				String localName = null;
				int eventCode;

				eventCode = xmlsr.next();
				if ((eventCode == XMLEvent.START_ELEMENT) || (eventCode == XMLEvent.END_ELEMENT)) {
					localName = xmlsr.getLocalName();
				}
				switch (eventCode) {
				case XMLEvent.START_ELEMENT:
					if (localName.equals(NUMMERAANDUIDING)) {
						building = null;
						status = null;
						startDate = null;
						endDate = null;
						number = null;
						postcode = null;
						inRecord = true;
					} else if (inRecord && localName.equals(NUMMERAANDUIDINGSTATUS)) {
						inStatus = true;
					} else if (inRecord && localName.equals(BEGINDATUMTIJDVAKGELDIGHEID)) {
						inStartDate = true;
					} else if (inRecord && localName.equals(EINDDATUMTIJDVAKGELDIGHEID)) {
						inEndDate = true;
					} else if (inRecord && localName.equals(HUISNUMMER)) {
						inNumber = true;
					} else if (inRecord && localName.equals(POSTCODE)) {
						inPostcode = true;
					} else if (inRecord && localName.equals(GERELATEERDEOPENBARERUIMTE)) {
						inRelated = true;
					} else if (inRelated && localName.equals(IDENTIFICATIE)) {
						inBuilding = true;
					}
					break;
				case XMLEvent.CHARACTERS:
					if (inStatus) status = xmlsr.getText();
					if (inNumber) number = Integer.valueOf(xmlsr.getText());
					if (inStartDate) startDate = Integer.valueOf(xmlsr.getText().substring(0, 8));
					if (inEndDate) endDate = Integer.valueOf(xmlsr.getText().substring(0, 8));
					if (inPostcode) postcode = xmlsr.getText();
					if (inBuilding) building = xmlsr.getText();
					break;
				case XMLEvent.END_ELEMENT:
					if (localName.equals(NUMMERAANDUIDING)) {
						Straat straat;

						if (status.equals(NAAMGEVINGUITGEGEVEN) && ((startDate == null) || (today >= startDate)) && ((endDate == null) || (today <= endDate)) && (postcode != null) && (building != null)) {
							Adres adres;

							adres = OPR.get(building);
							if (NUM.get(postcode) == null) {
								NUM.put(postcode, new HashMap<String, Straat>());
							}
							if ((straat = NUM.get(postcode).get(adres.getStraat())) == null) {
								straat = new Straat();
								straat.setListHuisnummer(new ArrayList<Integer>());
								straat.setPlaats(WPL.get(adres.getPlaats()));
								straat.setPostcode(postcode);
								straat.setNaam(adres.getStraat());
								NUM.get(postcode).put(adres.getStraat(), straat);
							}
							if (!straat.getListHuisnummer().contains(number)) {
								straat.getListHuisnummer().add(number);
							}
						}
						inRecord = false;
					} else if (inRecord && localName.equals(NUMMERAANDUIDINGSTATUS)) {
						inStatus = false;
					} else if (inRecord && localName.equals(BEGINDATUMTIJDVAKGELDIGHEID)) {
						inStartDate = false;
					} else if (inRecord && localName.equals(EINDDATUMTIJDVAKGELDIGHEID)) {
						inEndDate = false;
					} else if (inRecord && localName.equals(HUISNUMMER)) {
						inNumber = false;
					} else if (inRecord && localName.equals(POSTCODE)) {
						inPostcode = false;
					} else if (inRecord && localName.equals(GERELATEERDEOPENBARERUIMTE)) {
						inRelated = false;
					} else if (inRelated && localName.equals(IDENTIFICATIE)) {
						inBuilding = false;
					}
					break;
				}
			}
			return true;
		} catch (XMLStreamException e) {
		    logger.log(Level.SEVERE, "parse error NUM", e);
		    return false;
		}
	}

	private boolean fixup() {
		logger.info("fixing up intersecting data");
		for (String postcode: NUM.keySet()) {
			List<Straat> listStraat;

			listStraat = new ArrayList<Straat>(NUM.get(postcode).values());
			if (listStraat.size() > 1) {
				for (int a = 0; a < listStraat.size(); a++) {
					Straat straatA;

					straatA = listStraat.get(a);
					for (int b = a + 1; b < listStraat.size(); b++) {
						List<Integer> intersect;
						Straat straatB;
						boolean report = false;

						straatB = listStraat.get(b);
						intersect = straatA.getListHuisnummer().stream().filter(straatB.getListHuisnummer()::contains).collect(Collectors.toList());
						Collections.sort(intersect);
						if (intersect.size() > 0) {
							logger.log(Level.FINE, "Intersecting entries for " + straatA.getPostcode());
							logger.log(Level.FINE, " old A = " + straatA.toString());
							logger.log(Level.FINE, " old B = " + straatB.toString());
							logger.log(Level.FINE, " intersection = " + intersect.toString());
							report = true;
						}
						while (!intersect.isEmpty()) {
							Integer maxI = -1;
							Straat maxStraat = null;
							int maxCnt = -1;

							for (Integer i: intersect) {
								int cntA = 0, cntB = 0;

								if (straatA.getListHuisnummer().contains(intersect.get(0) - 1)) cntA += 1;
								if (straatA.getListHuisnummer().contains(intersect.get(0) + 1)) cntA += 1;
								if (straatA.getListHuisnummer().contains(intersect.get(0) - 2)) cntA += 3;
								if (straatA.getListHuisnummer().contains(intersect.get(0) + 2)) cntA += 3;
								if (straatA.getListHuisnummer().contains(intersect.get(0) - 4)) cntA += 2;
								if (straatA.getListHuisnummer().contains(intersect.get(0) + 4)) cntA += 2;
								if (straatB.getListHuisnummer().contains(intersect.get(0) - 1)) cntB += 1;
								if (straatB.getListHuisnummer().contains(intersect.get(0) + 1)) cntB += 1;
								if (straatB.getListHuisnummer().contains(intersect.get(0) - 2)) cntB += 3;
								if (straatB.getListHuisnummer().contains(intersect.get(0) + 2)) cntB += 3;
								if (straatB.getListHuisnummer().contains(intersect.get(0) - 4)) cntB += 2;
								if (straatB.getListHuisnummer().contains(intersect.get(0) + 4)) cntB += 2;
								if (cntA > cntB) {
									if (cntA > maxCnt) {
										maxCnt = cntA;
										maxI = i;
										maxStraat = straatB;
									}
								} else {
									if (cntB > maxCnt) {
										maxCnt = cntB;
										maxI = i;
										maxStraat = straatA;
									}
								}
							}
							maxStraat.getListHuisnummer().remove(maxI);
							intersect.remove(maxI);
						}
						if (report) {
							logger.log(Level.FINE, " new A = " + straatA.toString());
							logger.log(Level.FINE, " new B = " + straatB.toString());
						}
					}
				}
			}
		}
		return true;
	}

	private boolean determineRange() {
		logger.info("determining continuous ranges");
		for (String postcode: NUM.keySet()) {
			HashMap<Integer, Straat> h;
			HashMap<String, Straat> oldStraat, newStraat;
			List<Integer> listNumber;
			List<Straat> listNewStraat;
			Straat currentStraat = null;

			oldStraat = NUM.get(postcode);
			newStraat = new HashMap<String, Straat>();
			listNewStraat = new ArrayList<Straat>();
			NUM.put(postcode, newStraat);
			h = new HashMap<Integer, Straat>();
			for (Straat straat: oldStraat.values()) {
				for (Integer i: straat.getListHuisnummer()) {
					h.put(i, straat);				
				}
			}
			listNumber = new ArrayList<Integer>(h.keySet());
			Collections.sort(listNumber);
			for (Integer i: listNumber) {
				Straat straat;

				straat = h.get(i);
				if ((currentStraat == null) || (!currentStraat.getNaam().equals(straat.getNaam()))) {
					currentStraat = new Straat();
					currentStraat.setPlaats(straat.getPlaats());
					currentStraat.setPostcode(straat.getPostcode());
					currentStraat.setNaam(straat.getNaam());
					currentStraat.setHuisnummerStart(i);
					currentStraat.setEven(Boolean.FALSE);
					currentStraat.setOneven(Boolean.FALSE);
					listNewStraat.add(currentStraat);
				}
				currentStraat.setHuisnummerEinde(i);
				if ((i % 2) == 0) currentStraat.setEven(Boolean.TRUE);
				if ((i % 2) == 1) currentStraat.setOneven(Boolean.TRUE);
			}
			for (int a = 0; a < listNewStraat.size(); a++) {
				List<Straat> listCollapsed;
				Straat straatA;

				listCollapsed = new ArrayList<Straat>();
				straatA = listNewStraat.get(a);
				for (int b = a + 1; b < listNewStraat.size(); b++) {
					Straat straatB;

					straatB = listNewStraat.get(b);
					if ((!straatA.getNaam().equals(straatB.getNaam())) && ((straatA.isEven() && straatB.isEven()) || (straatA.isOneven() && straatB.isOneven()))) {
						break;
					}
					if (straatA.getNaam().equals(straatB.getNaam()) && (straatA.isEven() == straatB.isEven()) && (straatA.isOneven() == straatB.isOneven())) {
						listCollapsed.add(straatB);
					}
				}
				if (listCollapsed.size() > 0) {
					straatA.setHuisnummerEinde(listCollapsed.get(listCollapsed.size() - 1).getHuisnummerEinde());
					listNewStraat.removeAll(listCollapsed);
				}
			}
			for (Straat straat: listNewStraat) {
				newStraat.put(straat.getNaam() + "_" + straat.getHuisnummerStart(), straat);
			}
		}
		return true;
	}

	private boolean fillPOBox() {
		//Vacant postcodes extracted from https://nl.wikipedia.org/wiki/Postcodes_in_Nederland#Overzicht_van_alle_postcodes
		List<Integer> vacantPostcode = Arrays.asList(1280, 1290, 1370, 1450, 1480, 1490, 1570, 1580, 1590, 1690, 1880, 1890, 2090, 2780, 2790, 2880, 2890, 3090, 3210, 
				3390, 3490, 3590, 3610, 3650, 3660, 3670, 3680, 3690, 4070, 4080, 4090, 4210, 4290, 4390, 4590, 4910, 4950, 4960, 4970, 4980, 4990, 5180, 5190, 5380, 
				5470, 5510, 5770, 5780, 5790, 5810, 5880, 5890, 6090, 6110, 6250, 6310, 6330, 6340, 6380, 6390, 6480, 6490, 6750, 6760, 6770, 6780, 6790, 6890, 7110, 
				7170, 7180, 7190, 7280, 7290, 7340, 7370, 7710, 7790, 7850, 7870, 7910, 7930, 8290, 8360, 8370, 8450, 8460, 8480, 8510, 8540, 8570, 8580, 8590, 8610, 
				8630, 8640, 8660, 8670, 8680, 8690, 8740, 8750, 8760, 8780, 8790, 8810, 8820, 8840, 8870, 8950, 8960, 8970, 8980, 8990, 9020, 9030, 9070, 9090, 9120, 
				9140, 9180, 9190, 9210, 9310, 9360, 9370, 9380, 9390, 9440, 9630, 9660, 9690, 9810, 9820, 9910, 9940);
		HashMap<Integer, String> cities;
		String lastCity = null;

		logger.info("inserting post office boxes");
		cities = new HashMap<Integer, String>();
		for (HashMap<String, Straat> reeks: NUM.values()) {
			Straat straat;
			int i;

			straat = (Straat) reeks.values().toArray()[0];
			i = Integer.valueOf(straat.getPostcode().substring(0, 4));
			if (!cities.containsKey(i)) {
				cities.put(i, straat.getPlaats());
			}
		}
		for (int i = 1000; i < 9999; i += 10) {
			if ((!cities.containsKey(i)) && (!vacantPostcode.contains(i))) {
				for (int j = i; j < 9999; j++) {
					if (cities.containsKey(j)) {
						if (!cities.get(j).equals(lastCity)) {
							lastCity = cities.get(j);
							for (int k = i; k < j; k++) {
								HashMap<String, Straat> h;
								Straat straat;

								straat = new Straat();
								straat.setEven(true);
								straat.setOneven(true);
								straat.setPlaats(lastCity);
								straat.setPostcode(String.valueOf(k));
								straat.setNaam("Postbus");
								h = new HashMap<String, Straat>();
								h.put(straat.getNaam(), straat);
								NUM.put(straat.getPostcode(), h);
							}
						}
						break;
					}
				}
			}
		}
		for (int i = 0; i < 676; i++) {
			String postcode;

			postcode = "1060" + ((char) ('A' + (i / 26))) + ((char) ('A' + (i % 26)));
			if ((!NUM.containsKey(postcode)) && (!postcode.equals("1060SA")) && (!postcode.equals("1060SD")) && (!postcode.equals("1060SS"))) {
				HashMap<String, Straat> h;
				Straat straat;
	
				straat = new Straat();
				straat.setEven(true);
				straat.setOneven(true);
				straat.setPlaats("Amsterdam");
				straat.setPostcode(postcode);
				straat.setNaam("Postbus");
				h = new HashMap<String, Straat>();
				h.put(straat.getNaam(), straat);
				NUM.put(straat.getPostcode(), h);
			}
		}
		return true;
	}

	private boolean write(String version, String outputFile, String tableName) {
		logger.info("writing to " + outputFile);
		try (Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"))) {
			List<String> list;
			boolean isFirst = true;

			list = new ArrayList<String>(NUM.keySet());
			Collections.sort(list);
			out.write("--created with " + VERSION + "\n");
			out.write("--" + URL + "\n");
			out.write("--data version: " + version + "\n");
			out.write("INSERT INTO " + (tableName == null ? "reeks" : tableName) + " (postcode_numeriek, postcode_letters, straat, plaats, huisnummer_start, huisnummer_einde, huisnummer_even, huisnummer_oneven) VALUES \n");
			for (String postcode: list) {
				List<Straat> listStraat;

				listStraat = new ArrayList<Straat>(NUM.get(postcode).values());
				Collections.sort(listStraat, (a, b) -> a.getHuisnummerStart().compareTo(b.getHuisnummerStart()));
				for (Straat straat: listStraat) {
					if (isFirst) {
						isFirst = false;
					} else {
						out.write(",\n");
					}
					out.write('(');
					out.write(quote(straat.getPostcode().substring(0, 4)));
					out.write(',');
					if (straat.getPostcode().length() == 4) {
						out.write("NULL");
					} else {
						out.write(quote(straat.getPostcode().substring(4, 6)));
					}
					out.write(',');
					out.write(quote(straat.getNaam()));
					out.write(',');
					out.write(quote(straat.getPlaats()));
					out.write(',');
					if (straat.getHuisnummerStart() == null) {
						out.write("NULL");
					} else {
						out.write(straat.getHuisnummerStart().toString());
					}
					out.write(',');
					if (straat.getHuisnummerEinde() == null) {
						out.write("NULL");
					} else {
						out.write(straat.getHuisnummerEinde().toString());
					}
					out.write(',');
					out.write(straat.isEven() ? "true" : "false");
					out.write(',');
					out.write(straat.isOneven() ? "true" : "false");
					out.write(')');
				}
			}
			out.write(";\n");
			return true;
		} catch (IOException e) {
		    logger.log(Level.SEVERE, "error writing output file", e);
		    return false;
		}
	}

	private String quote(String s) {
		return "'" + s.replaceAll("'", "''") + "'";
	}

	public static void main(String[] args) {
		String rootFile = null, outputFile = null, tableName = null;
		int arg = 0;

		if ((args.length >= 0) && args[arg].equals("-debug")) {
			arg++;
			logger.setLevel(Level.FINEST);
			for (Handler handler : Logger.getLogger("").getHandlers()) handler.setLevel(logger.getLevel());
			logger.fine("debug logging enabled");
		}
		if (args.length >= (2 + arg)) {
			rootFile = args[arg++];
			outputFile = args[arg++];
		}
		if (args.length >= (1 + arg)) {
			tableName = args[arg++];
		}
		if (rootFile != null) {
			boolean res;

			logger.info("started " + VERSION);
			res = new Converter().convert(rootFile, outputFile, tableName);
			logger.info("finished");
			if (!res) System.exit(1);
		} else {
			//retrieve inspireadressen.zip from regularly updated http://geodata.nationaalgeoregister.nl/inspireadressen/extract/inspireadressen.zip
			logger.info(VERSION);
			logger.info(URL);
			logger.info("syntax: postcode2sql [-debug] location-of-inspireadressen.zip output.sql [tablename]");
		}
	}
}
