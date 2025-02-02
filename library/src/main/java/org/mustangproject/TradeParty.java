package org.mustangproject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.mustangproject.ZUGFeRD.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/***
 * A organisation, i.e. usually a company
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeParty implements IZUGFeRDExportableTradeParty {

	protected String name, zip, street, location, country;
	protected String taxID = null, vatID = null;
	protected String ID = null;
	protected String additionalAddress = null;
	protected List<BankDetails> bankDetails = new ArrayList<>();
	protected List<IZUGFeRDTradeSettlementDebit> debitDetails = new ArrayList<>();
	protected Contact contact = null;
	protected LegalOrganisation legalOrg = null;

	/**
	 * Default constructor.
	 * Probably a bad idea but might be needed by jackson or similar
	 */
	public TradeParty() {

	}


	/***
	 *
	 * @param name of the company
	 * @param street street and number (use setAdditionalAddress for more parts)
	 * @param zip postcode of the company
	 * @param location city of the company
	 * @param country two letter ISO code
	 */
	public TradeParty(String name, String street, String zip, String location, String country) {
		this.name = name;
		this.street = street;
		this.zip = zip;
		this.location = location;
		this.country = country;

	}

	/***
	 * XML parsing constructor
	 * @param nodes the nodelist returned e.g. from xpath
	 */
	public TradeParty(NodeList nodes) {
/**
 * <ram:SellerTradeParty>
 *  <ram:ID>LIEF-987654321</ram:ID>
 *  <ram:Name>Max Mustermann IT GmbH</ram:Name>
 *  <ram:DefinedTradeContact>
 *  <ram:PersonName>Herr Treudiener</ram:PersonName>
 *  <ram:TelephoneUniversalCommunication>
 *  <ram:CompleteNumber>+49 1234-98765-12</ram:CompleteNumber>
 *  </ram:TelephoneUniversalCommunication>
 *  <ram:EmailURIUniversalCommunication>
 *  <ram:URIID>treudiener@max-mustermann-it.de</ram:URIID>
 *  </ram:EmailURIUniversalCommunication>
 *  </ram:DefinedTradeContact>
 *  <ram:PostalTradeAddress>
 *  <ram:PostcodeCode>12345</ram:PostcodeCode>
 *  <ram:LineOne>Musterstraße 1</ram:LineOne>
 *  <ram:CityName>Musterstadt</ram:CityName>
 *  <ram:CountryID>DE</ram:CountryID>
 *  </ram:PostalTradeAddress>
 *  <ram:SpecifiedTaxRegistration>
 *  <ram:ID schemeID="VA">DE123456789</ram:ID>
 *  </ram:SpecifiedTaxRegistration>
 *  </ram:SellerTradeParty>
 */
		if (nodes.getLength() > 0) {

			for (int nodeIndex = 0; nodeIndex < nodes.getLength(); nodeIndex++) {
				//nodes.item(i).getTextContent())) {
				Node currentItemNode = nodes.item(nodeIndex);
				NodeList itemChilds = currentItemNode.getChildNodes();
				for (int itemChildIndex = 0; itemChildIndex < itemChilds.getLength(); itemChildIndex++) {
					if (itemChilds.item(itemChildIndex).getLocalName() != null) {

						if (itemChilds.item(itemChildIndex).getLocalName().equals("Name")) {
							setName(itemChilds.item(itemChildIndex).getTextContent());
						}
						if (itemChilds.item(itemChildIndex).getLocalName().equals("DefinedTradeContact")) {
							NodeList contact = itemChilds.item(itemChildIndex).getChildNodes();
							setContact(new Contact(contact));
						}

						if (itemChilds.item(itemChildIndex).getLocalName().equals("PostalTradeAddress")) {
							NodeList postal = itemChilds.item(itemChildIndex).getChildNodes();
							for (int postalChildIndex = 0; postalChildIndex < postal.getLength(); postalChildIndex++) {
								if (postal.item(postalChildIndex).getLocalName() != null) {
									if (postal.item(postalChildIndex).getLocalName().equals("LineOne")) {
										setStreet(postal.item(postalChildIndex).getTextContent());
									}
									if (postal.item(postalChildIndex).getLocalName().equals("LineTwo")) {
										setAdditionalAddress(postal.item(postalChildIndex).getTextContent());
									}
									if (postal.item(postalChildIndex).getLocalName().equals("CityName")) {
										setLocation(postal.item(postalChildIndex).getTextContent());
									}
									if (postal.item(postalChildIndex).getLocalName().equals("PostcodeCode")) {
										setZIP(postal.item(postalChildIndex).getTextContent());
									}
									if (postal.item(postalChildIndex).getLocalName().equals("CountryID")) {
										setCountry(postal.item(postalChildIndex).getTextContent());
									}

								}
							}

						}

						if (itemChilds.item(itemChildIndex).getLocalName().equals("SpecifiedTaxRegistration")) {
							NodeList taxChilds = itemChilds.item(itemChildIndex).getChildNodes();
							for (int taxChildIndex = 0; taxChildIndex < taxChilds.getLength(); taxChildIndex++) {
								if (taxChilds.item(taxChildIndex).getLocalName() != null) {
									if ((taxChilds.item(taxChildIndex).getLocalName().equals("ID"))) {
										if (taxChilds.item(taxChildIndex).getAttributes().getNamedItem("schemeID") != null) {
											if (taxChilds.item(taxChildIndex).getAttributes().getNamedItem("schemeID").getNodeValue().equals("VA")) {
												setVATID(taxChilds.item(taxChildIndex).getFirstChild().getNodeValue());
											}
											if (taxChilds.item(taxChildIndex).getAttributes().getNamedItem("schemeID").getNodeValue().equals("FC")) {
												setTaxID(taxChilds.item(taxChildIndex).getFirstChild().getNodeValue());
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	@Override
	public String getID() {
		return ID;
	}

	/**
	 * if it's a customer, this can e.g. be the customer ID
	 *
	 * @param ID customer/seller number
	 * @return fluent setter
	 */
	public TradeParty setID(String ID) {
		this.ID = ID;
		return this;
	}

	/***
	 * (optional) a named contact person
	 * @see Contact
	 * @param c the named contact person
	 * @return fluent setter
	 */
	public TradeParty setContact(Contact c) {
		this.contact = c;
		return this;
	}

	/***
	 * required (for senders, if payment is not debit): the BIC and IBAN
	 * @param s bank credentials
	 * @return fluent setter
	 */
	public TradeParty addBankDetails(BankDetails s) {
		bankDetails.add(s);
		return this;
	}

	/**
	 * (optional)
	 *
	 * @param debitDetail
	 * @return fluent setter
	 */
	public TradeParty addDebitDetails(IZUGFeRDTradeSettlementDebit debitDetail) {
		debitDetails.add(debitDetail);
		return this;
	}

	@Override
	public IZUGFeRDLegalOrganisation getLegalOrganisation() {
		return legalOrg;
	}

	public TradeParty setLegalOrganisation(LegalOrganisation legalOrganisation) {
		legalOrg=legalOrganisation;
		return this;
	}

	public List<BankDetails> getBankDetails() {
		return bankDetails;
	}

	/***
	 * a general tax ID
	 * @param taxID tax number of the organisation
	 * @return fluent setter
	 */
	public TradeParty addTaxID(String taxID) {
		this.taxID = taxID;
		return this;
	}

	/***
	 * the USt-ID
	 * @param vatID Ust-ID
	 * @return fluent setter
	 */
	public TradeParty addVATID(String vatID) {
		this.vatID = vatID;
		return this;
	}

	@Override
	public String getVATID() {
		return vatID;
	}

	public TradeParty setVATID(String VATid) {
		this.vatID = VATid;
		return this;
	}

	@Override
	public String getTaxID() {
		return taxID;
	}

	public TradeParty setTaxID(String tax) {
		this.taxID = tax;
		return this;
	}

	public String getName() {
		return name;
	}


	/***
	 * required, usually done in the constructor: the complete name of the organisation
	 * @param name complete legal name
	 * @return fluent setter
	 */
	public TradeParty setName(String name) {
		this.name = name;
		return this;
	}


	public String getZIP() {
		return zip;
	}

	/***
	 * usually set in the constructor, required for recipients in german invoices: postcode
	 * @param zip postcode
	 * @return fluent setter
	 */
	public TradeParty setZIP(String zip) {
		this.zip = zip;
		return this;
	}

	@Override
	public String getStreet() {
		return street;
	}

	/***
	 * usually set in constructor, required in germany, street and house number
	 * @param street street name and number
	 * @return fluent setter
	 */
	public TradeParty setStreet(String street) {
		this.street = street;
		return this;
	}

	@Override
	public String getLocation() {
		return location;
	}

	/***
	 * usually set in constructor, usually required in germany, the city of the organisation
	 * @param location city
	 * @return fluent setter
	 */
	public TradeParty setLocation(String location) {
		this.location = location;
		return this;
	}

	@Override
	public String getCountry() {
		return country;
	}

	/***
	 * two-letter ISO code of the country
	 * @param country two-letter-code
	 * @return fluent setter
	 */
	public TradeParty setCountry(String country) {
		this.country = country;
		return this;
	}


	public String getVatID() {
		return vatID;
	}

	@Override
	public IZUGFeRDExportableContact getContact() {
		return contact;
	}

	public IZUGFeRDTradeSettlement[] getAsTradeSettlement() {
		if (bankDetails.isEmpty() && debitDetails.isEmpty()) {
			return null;
		}
		List<IZUGFeRDTradeSettlement> tradeSettlements = Stream.concat(bankDetails.stream(), debitDetails.stream())
				.map(IZUGFeRDTradeSettlement.class::cast)
				.collect(Collectors.toList());

		IZUGFeRDTradeSettlement[] result = new IZUGFeRDTradeSettlement[tradeSettlements.size()];
		for (int i = 0; i < tradeSettlements.size(); i++) {
			IZUGFeRDTradeSettlement izugFeRDTradeSettlement = tradeSettlements.get(i);
			result[i] = izugFeRDTradeSettlement;
		}
		return result;
	}

	@Override
	public String getAdditionalAddress() {
		return additionalAddress;
	}


	/***
	 * additional parts of the address, e.g. which floor.
	 * Street address will become "lineOne", this will become "lineTwo"
	 * @param additionalAddress additional address description
	 * @return fluent setter
	 */
	public TradeParty setAdditionalAddress(String additionalAddress) {
		this.additionalAddress = additionalAddress;
		return this;
	}


}
