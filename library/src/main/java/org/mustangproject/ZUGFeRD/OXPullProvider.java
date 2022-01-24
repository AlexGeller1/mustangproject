/**
 * *********************************************************************
 * <p>
 * Copyright 2018 Jochen Staerk
 * <p>
 * Use is subject to license terms.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at http://www.apache.org/licenses/LICENSE-2.0.
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * <p>
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * **********************************************************************
 */
package org.mustangproject.ZUGFeRD;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.mustangproject.EStandard;
import org.mustangproject.FileAttachment;
import org.mustangproject.XMLTools;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.mustangproject.ZUGFeRD.ZUGFeRDDateFormat.DATE;
import static org.mustangproject.ZUGFeRD.model.DocumentCodeTypeConstants.CORRECTEDINVOICE;
import static org.mustangproject.ZUGFeRD.model.TaxCategoryCodeTypeConstants.CATEGORY_CODES_WITH_EXEMPTION_REASON;

public class OXPullProvider extends ZUGFeRD2PullProvider implements IXMLProvider {

	protected IExportableTransaction trans;
	protected TransactionCalculator calc;
	private String paymentTermsDescription;
	protected Profile profile = Profiles.getByName(EStandard.orderx,"basic", 1);


	@Override
	public void generateXML(IExportableTransaction trans) {
		this.trans = trans;
		this.calc = new TransactionCalculator(trans);

		boolean hasDueDate = false;
		final SimpleDateFormat germanDateFormat = new SimpleDateFormat("dd.MM.yyyy");

		String exemptionReason = "";

		if (trans.getPaymentTermDescription() != null) {
			paymentTermsDescription = trans.getPaymentTermDescription();
		}

		if ((paymentTermsDescription == null) && (trans.getDocumentCode() != CORRECTEDINVOICE)/* && (trans.getDocumentCode() != DocumentCodeTypeConstants.CREDITNOTE)*/) {
			paymentTermsDescription = "Zahlbar ohne Abzug bis " + germanDateFormat.format(trans.getDueDate());

		}

		String senderReg = "";
		if (trans.getOwnOrganisationFullPlaintextInfo() != null) {
			senderReg = "" + "<ram:IncludedNote>\n" + "		<ram:Content>\n"
					+ XMLTools.encodeXML(trans.getOwnOrganisationFullPlaintextInfo()) + "		</ram:Content>\n"
					+ "<ram:SubjectCode>REG</ram:SubjectCode>\n" + "</ram:IncludedNote>\n";

		}

		String rebateAgreement = "";
		if (trans.rebateAgreementExists()) {
			rebateAgreement = "<ram:IncludedNote>\n" + "		<ram:Content>"
					+ "Es bestehen Rabatt- und Bonusvereinbarungen.</ram:Content>\n"
					+ "<ram:SubjectCode>AAK</ram:SubjectCode>\n" + "</ram:IncludedNote>\n";
		}

		String subjectNote = "";
		if (trans.getSubjectNote() != null) {
			subjectNote = "<ram:IncludedNote>\n" + "		<ram:Content>"
					+ XMLTools.encodeXML(trans.getSubjectNote()) + "</ram:Content>\n"
					+ "</ram:IncludedNote>\n";
		}

		String typecode = "220";
		/*if (trans.getDocumentCode() != null) {
			typecode = trans.getDocumentCode();
		}*/
		String notes = "";
		if (trans.getNotes() != null) {
			for (final String currentNote : trans.getNotes()) {
				notes = notes + "<ram:IncludedNote><ram:Content>" + XMLTools.encodeXML(currentNote) + "</ram:Content></ram:IncludedNote>";

			}
		}
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"

				+ "<rsm:SCRDMCCBDACIOMessageStructure\n" +
				"xmlns:rsm=\"urn:un:unece:uncefact:data:SCRDMCCBDACIOMessageStructure:100\"\n" +
				"xmlns:udt=\"urn:un:unece:uncefact:data:standard:UnqualifiedDataType:128\"\n" +
				"xmlns:qdt=\"urn:un:unece:uncefact:data:standard:QualifiedDataType:128\"\n" +
				"xmlns:ram=\"urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:128\"\n" +
				"xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
				// + "
				// xsi:schemaLocation=\"urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100
				// ../Schema/ZUGFeRD1p0.xsd\""
				+ "	<rsm:ExchangedDocumentContext>\n"
				// + "
				// <ram:TestIndicator><udt:Indicator>"+testBooleanStr+"</udt:Indicator></ram:TestIndicator>\n"
				//
				+ "<ram:BusinessProcessSpecifiedDocumentContextParameter>\n"
				+ "<ram:ID>A1</ram:ID>\n"
				+ "</ram:BusinessProcessSpecifiedDocumentContextParameter>\n"
				+ "		<ram:GuidelineSpecifiedDocumentContextParameter>\n"
				+ "			<ram:ID>" + getProfile().getID() + "</ram:ID>\n"
				+ "		</ram:GuidelineSpecifiedDocumentContextParameter>\n"
				+ "	</rsm:ExchangedDocumentContext>\n"
				+ "	<rsm:ExchangedDocument>\n"
				+ "		<ram:ID>" + XMLTools.encodeXML(trans.getNumber()) + "</ram:ID>\n"
				// + " <ram:Name>RECHNUNG</ram:Name>\n"
				// + "		<ram:TypeCode>380</ram:TypeCode>\n"
				+ "		<ram:TypeCode>" + typecode + "</ram:TypeCode>\n"
				+ "		<ram:IssueDateTime>"
				+ DATE.udtFormat(trans.getIssueDate()) + "</ram:IssueDateTime>\n" // date
				+ notes
				+ subjectNote
				+ rebateAgreement
				+ senderReg

				+ "	</rsm:ExchangedDocument>\n"
				+ "	<rsm:SupplyChainTradeTransaction>\n";
		int lineID = 0;
		for (final IZUGFeRDExportableItem currentItem : trans.getZFItems()) {
			lineID++;
			if (currentItem.getProduct().getTaxExemptionReason() != null) {
			//	exemptionReason = "<ram:ExemptionReason>" + XMLTools.encodeXML(currentItem.getProduct().getTaxExemptionReason()) + "</ram:ExemptionReason>";
			}
			notes = "";
			if (currentItem.getNotes() != null) {
				for (final String currentNote : currentItem.getNotes()) {
					notes = notes + "<ram:IncludedNote><ram:Content>" + XMLTools.encodeXML(currentNote) + "</ram:Content></ram:IncludedNote>";

				}
			}
			final LineCalculator lc = new LineCalculator(currentItem);
			xml = xml + "		<ram:IncludedSupplyChainTradeLineItem>\n" +
					"			<ram:AssociatedDocumentLineDocument>\n"
					+ "				<ram:LineID>" + lineID + "</ram:LineID>\n"
					+ notes
					+ "			</ram:AssociatedDocumentLineDocument>\n"

					+ "			<ram:SpecifiedTradeProduct>\n";
			// + " <GlobalID schemeID=\"0160\">4012345001235</GlobalID>\n"
			if (currentItem.getProduct().getSellerAssignedID() != null) {
				xml = xml + "				<ram:SellerAssignedID>"
						+ XMLTools.encodeXML(currentItem.getProduct().getSellerAssignedID()) + "</ram:SellerAssignedID>\n";
			}
			if (currentItem.getProduct().getBuyerAssignedID() != null) {
				xml = xml + "				<ram:BuyerAssignedID>"
						+ XMLTools.encodeXML(currentItem.getProduct().getBuyerAssignedID()) + "</ram:BuyerAssignedID>\n";
			}
			String allowanceChargeStr = "";
			if (currentItem.getItemAllowances() != null && currentItem.getItemAllowances().length > 0) {
				for (final IZUGFeRDAllowanceCharge allowance : currentItem.getItemAllowances()) {
					allowanceChargeStr += getAllowanceChargeStr(allowance, currentItem);
				}
			}
			if (currentItem.getItemCharges() != null && currentItem.getItemCharges().length > 0) {
				for (final IZUGFeRDAllowanceCharge charge : currentItem.getItemCharges()) {
					allowanceChargeStr += getAllowanceChargeStr(charge, currentItem);

				}
			}


			xml = xml + "					<ram:Name>" + XMLTools.encodeXML(currentItem.getProduct().getName()) + "</ram:Name>\n"
					+ "				<ram:Description>" + XMLTools.encodeXML(currentItem.getProduct().getDescription())
					+ "</ram:Description>\n"
					+ "			</ram:SpecifiedTradeProduct>\n"

					+ "			<ram:SpecifiedLineTradeAgreement>\n";
		/*	if (currentItem.getReferencedDocuments() != null) {
				for (IReferencedDocument currentReferencedDocument : currentItem.getReferencedDocuments()) {
					xml = xml + "<ram:AdditionalReferencedDocument>\n" +
							"<ram:IssuerAssignedID>" + XMLTools.encodeXML(currentReferencedDocument.getIssuerAssignedID()) + "</ram:IssuerAssignedID>\n" +
							"<ram:TypeCode>" + XMLTools.encodeXML(currentReferencedDocument.getTypeCode()) + "</ram:TypeCode>\n" +
							"<ram:ReferenceTypeCode>" + XMLTools.encodeXML(currentReferencedDocument.getReferenceTypeCode()) + "</ram:ReferenceTypeCode>\n" +
							"</ram:AdditionalReferencedDocument>\n";


				}

			}*/
			if (currentItem.getBuyerOrderReferencedDocumentLineID() != null) {
				xml = xml + "				<ram:BuyerOrderReferencedDocument> \n"
						+ "					<ram:LineID>" + XMLTools.encodeXML(currentItem.getBuyerOrderReferencedDocumentLineID()) + "</ram:LineID>\n"
						+ "				</ram:BuyerOrderReferencedDocument>\n";

			}
			xml = xml + "				<ram:GrossPriceProductTradePrice>\n"
					+ "					<ram:ChargeAmount>" + priceFormat(lc.getPriceGross())
					+ "</ram:ChargeAmount>\n" //currencyID=\"EUR\"
					+ "<ram:BasisQuantity unitCode=\"" + XMLTools.encodeXML(currentItem.getProduct().getUnit())
					+ "\">" + quantityFormat(currentItem.getBasisQuantity()) + "</ram:BasisQuantity>\n"
					+ allowanceChargeStr
					// + " <AppliedTradeAllowanceCharge>\n"
					// + " <ChargeIndicator>false</ChargeIndicator>\n"
					// + " <ActualAmount currencyID=\"EUR\">0.6667</ActualAmount>\n"
					// + " <Reason>Rabatt</Reason>\n"
					// + " </AppliedTradeAllowanceCharge>\n"
					+ "				</ram:GrossPriceProductTradePrice>\n"
					+ "				<ram:NetPriceProductTradePrice>\n"
					+ "					<ram:ChargeAmount>" + priceFormat(lc.getPrice())
					+ "</ram:ChargeAmount>\n" // currencyID=\"EUR\"
					+ "					<ram:BasisQuantity unitCode=\"" + XMLTools.encodeXML(currentItem.getProduct().getUnit())
					+ "\">" + quantityFormat(currentItem.getBasisQuantity()) + "</ram:BasisQuantity>\n"
					+ "				</ram:NetPriceProductTradePrice>\n"
					+ "			</ram:SpecifiedLineTradeAgreement>\n"

					+ "			<ram:SpecifiedLineTradeDelivery>\n"
					+ "				<ram:RequestedQuantity unitCode=\"" + XMLTools.encodeXML(currentItem.getProduct().getUnit()) + "\">"
					+ quantityFormat(currentItem.getQuantity()) + "</ram:RequestedQuantity>\n"
					+ "			</ram:SpecifiedLineTradeDelivery>\n"
					+ "			<ram:SpecifiedLineTradeSettlement>\n"
					+ "				<ram:ApplicableTradeTax>\n"
					+ "					<ram:TypeCode>VAT</ram:TypeCode>\n"
					+ exemptionReason
					+ "					<ram:CategoryCode>" + currentItem.getProduct().getTaxCategoryCode() + "</ram:CategoryCode>\n"

					+ "					<ram:RateApplicablePercent>"
					+ vatFormat(currentItem.getProduct().getVATPercent()) + "</ram:RateApplicablePercent>\n"
					+ "				</ram:ApplicableTradeTax>\n";
			if ((currentItem.getDetailedDeliveryPeriodFrom() != null) || (currentItem.getDetailedDeliveryPeriodTo() != null)) {
				xml = xml + "<ram:BillingSpecifiedPeriod>";
				if (currentItem.getDetailedDeliveryPeriodFrom() != null) {
					xml = xml + "<ram:StartDateTime>" + DATE.udtFormat(currentItem.getDetailedDeliveryPeriodFrom()) + "</ram:StartDateTime>";
				}
				if (currentItem.getDetailedDeliveryPeriodTo() != null) {
					xml = xml + "<ram:EndDateTime>" + DATE.udtFormat(currentItem.getDetailedDeliveryPeriodTo()) + "</ram:EndDateTime>";
				}
				xml = xml + "</ram:BillingSpecifiedPeriod>";

			}

			xml = xml + "				<ram:SpecifiedTradeSettlementLineMonetarySummation>\n"
					+ "					<ram:LineTotalAmount>" + currencyFormat(lc.getItemTotalNetAmount())
					+ "</ram:LineTotalAmount>\n" // currencyID=\"EUR\"
					+ "				</ram:SpecifiedTradeSettlementLineMonetarySummation>\n";
		/*	if (currentItem.getAdditionalReferencedDocumentID() != null) {
				xml = xml + "			<ram:AdditionalReferencedDocument><ram:IssuerAssignedID>" + currentItem.getAdditionalReferencedDocumentID() + "</ram:IssuerAssignedID><ram:TypeCode>130</ram:TypeCode></ram:AdditionalReferencedDocument>\n";

			}*/
			xml = xml + "			</ram:SpecifiedLineTradeSettlement>\n"
					+ "		</ram:IncludedSupplyChainTradeLineItem>\n";

		}

		xml = xml + "		<ram:ApplicableHeaderTradeAgreement>\n";
		if (trans.getReferenceNumber() != null) {
			xml = xml + "			<ram:BuyerReference>" + XMLTools.encodeXML(trans.getReferenceNumber()) + "</ram:BuyerReference>\n";

		}
		xml = xml + "			<ram:SellerTradeParty>\n"
				+ getTradePartyAsXML(trans.getSender(), true, false)
				+ "			</ram:SellerTradeParty>\n"
				+ "			<ram:BuyerTradeParty>\n";
		// + " <ID>GE2020211</ID>\n"
		// + " <GlobalID schemeID=\"0088\">4000001987658</GlobalID>\n"

		xml += getTradePartyAsXML(trans.getRecipient(), false, false);
		xml += "			</ram:BuyerTradeParty>\n";

		if (trans.getSellerOrderReferencedDocumentID() != null) {
			xml = xml + "   <ram:SellerOrderReferencedDocument>\n"
					+ "       <ram:IssuerAssignedID>"
					+ XMLTools.encodeXML(trans.getSellerOrderReferencedDocumentID()) + "</ram:IssuerAssignedID>\n"
					+ "   </ram:SellerOrderReferencedDocument>\n";
		}
		if (trans.getBuyerOrderReferencedDocumentID() != null) {
			xml = xml + "   <ram:BuyerOrderReferencedDocument>\n"
					+ "       <ram:IssuerAssignedID>"
					+ XMLTools.encodeXML(trans.getBuyerOrderReferencedDocumentID()) + "</ram:IssuerAssignedID>\n"
					+ "   </ram:BuyerOrderReferencedDocument>\n";
		}
		if (trans.getContractReferencedDocument() != null) {
			xml = xml + "   <ram:ContractReferencedDocument>\n"
					+ "       <ram:IssuerAssignedID>"
					+ XMLTools.encodeXML(trans.getContractReferencedDocument()) + "</ram:IssuerAssignedID>\n"
					+ "    </ram:ContractReferencedDocument>\n";
		}

		// Additional Documents of XRechnung (Rechnungsbegruendende Unterlagen - BG-24 XRechnung)
		if (trans.getAdditionalReferencedDocuments() != null) {
			for (final FileAttachment f : trans.getAdditionalReferencedDocuments()) {
				final String documentContent = new String(Base64.getEncoder().encodeToString(f.getData()));
				xml = xml + "  <ram:AdditionalReferencedDocument>\n"
						+ "    <ram:IssuerAssignedID>" + f.getFilename() + "</ram:IssuerAssignedID>\n"
						+ "    <ram:TypeCode>916</ram:TypeCode>\n"
						+ "    <ram:Name>" + f.getDescription() + "</ram:Name>\n"
						+ "    <ram:AttachmentBinaryObject mimeCode=\"" + f.getMimetype() + "\"\n"
						+ "      filename=\"" + f.getFilename() + "\">" + documentContent + "</ram:AttachmentBinaryObject>\n"
						+ "  </ram:AdditionalReferencedDocument>\n";
			}
		}

		if (trans.getSpecifiedProcuringProjectID() != null) {
			xml = xml + "   <ram:SpecifiedProcuringProject>\n"
					+ "       <ram:ID>"
					+ XMLTools.encodeXML(trans.getSpecifiedProcuringProjectID()) + "</ram:ID>\n";
			if (trans.getSpecifiedProcuringProjectName() != null) {
				xml += "       <ram:Name >" + XMLTools.encodeXML(trans.getSpecifiedProcuringProjectName()) + "</ram:Name>\n";
			}
			xml += "    </ram:SpecifiedProcuringProject>\n";
		}
		xml = xml + "		</ram:ApplicableHeaderTradeAgreement>\n"
				+ "		<ram:ApplicableHeaderTradeDelivery>\n";
		if (this.trans.getDeliveryAddress() != null) {
			xml += "<ram:ShipToTradeParty>" +
					getTradePartyAsXML(this.trans.getDeliveryAddress(), false, true) +
					"</ram:ShipToTradeParty>";
		}
/*
		xml += "			<ram:ActualDeliverySupplyChainEvent>\n"
				+ "				<ram:OccurrenceDateTime>";

		if (trans.getDeliveryDate() != null) {
			xml += DATE.udtFormat(trans.getDeliveryDate());
		} else {
			throw new IllegalStateException("No delivery date provided");
		}
		xml += "</ram:OccurrenceDateTime>\n";
		xml += "			</ram:ActualDeliverySupplyChainEvent>\n"

 */
				/*
				 * + "			<DeliveryNoteReferencedDocument>\n" +
				 * "				<IssueDateTime format=\"102\">20130603</IssueDateTime>\n" +
				 * "				<ID>2013-51112</ID>\n" +
				 * "			</DeliveryNoteReferencedDocument>\n"
				 */
				xml+= "		</ram:ApplicableHeaderTradeDelivery>\n" + "		<ram:ApplicableHeaderTradeSettlement>\n"
	//			+ "			<ram:PaymentReference>" + XMLTools.encodeXML(trans.getNumber()) + "</ram:PaymentReference>\n"
				+ "			<ram:OrderCurrencyCode>" + trans.getCurrency() + "</ram:OrderCurrencyCode>\n";

		if (trans.getTradeSettlementPayment() != null) {
			for (final IZUGFeRDTradeSettlementPayment payment : trans.getTradeSettlementPayment()) {
				if (payment != null) {
					hasDueDate = true;
				//	xml += payment.getSettlementXML();
				}
			}
		}
		if (trans.getTradeSettlement() != null) {
			for (final IZUGFeRDTradeSettlement payment : trans.getTradeSettlement()) {
				if (payment != null) {
					if (payment instanceof IZUGFeRDTradeSettlementPayment) {
						hasDueDate = true;
					}
				//	xml += payment.getSettlementXML();
				}
			}
		}
		if ((trans.getDocumentCode() == CORRECTEDINVOICE)/*||(trans.getDocumentCode() == DocumentCodeTypeConstants.CREDITNOTE)*/) {
			hasDueDate = false;
		}

		final Map<BigDecimal, VATAmount> VATPercentAmountMap = calc.getVATPercentAmountMap();
		for (final BigDecimal currentTaxPercent : VATPercentAmountMap.keySet()) {
			final VATAmount amount = VATPercentAmountMap.get(currentTaxPercent);
			if (amount != null) {
				final String amountCategoryCode = amount.getCategoryCode();
				final boolean displayExemptionReason = CATEGORY_CODES_WITH_EXEMPTION_REASON.contains(amountCategoryCode);
	/*			xml += "			<ram:ApplicableTradeTax>\n"
						+ "				<ram:CalculatedAmount>" + currencyFormat(amount.getCalculated())
						+ "</ram:CalculatedAmount>\n" //currencyID=\"EUR\"
						+ "				<ram:TypeCode>VAT</ram:TypeCode>\n"
						+ (displayExemptionReason ? exemptionReason : "")
						+ "				<ram:BasisAmount>" + currencyFormat(amount.getBasis()) + "</ram:BasisAmount>\n" // currencyID=\"EUR\"
						+ "				<ram:CategoryCode>" + amountCategoryCode + "</ram:CategoryCode>\n"
						+ "				<ram:RateApplicablePercent>"
						+ vatFormat(currentTaxPercent) + "</ram:RateApplicablePercent>\n" + "			</ram:ApplicableTradeTax>\n";

	 */
			}
		}
		if ((trans.getDetailedDeliveryPeriodFrom() != null) || (trans.getDetailedDeliveryPeriodTo() != null)) {
			xml = xml + "<ram:BillingSpecifiedPeriod>";
			if (trans.getDetailedDeliveryPeriodFrom() != null) {
				xml = xml + "<ram:StartDateTime>" + DATE.udtFormat(trans.getDetailedDeliveryPeriodFrom()) + "</ram:StartDateTime>";
			}
			if (trans.getDetailedDeliveryPeriodTo() != null) {
				xml = xml + "<ram:EndDateTime>" + DATE.udtFormat(trans.getDetailedDeliveryPeriodTo()) + "</ram:EndDateTime>";
			}
			xml = xml + "</ram:BillingSpecifiedPeriod>";


		}

		if ((trans.getZFCharges() != null) && (trans.getZFCharges().length > 0)) {

			for (final BigDecimal currentTaxPercent : VATPercentAmountMap.keySet()) {
				if (calc.getChargesForPercent(currentTaxPercent).compareTo(BigDecimal.ZERO) != 0) {


					xml = xml + "	 <ram:SpecifiedTradeAllowanceCharge>\n" +
							"        <ram:ChargeIndicator>\n" +
							"          <udt:Indicator>true</udt:Indicator>\n" +
							"        </ram:ChargeIndicator>\n" +
							"        <ram:ActualAmount>" + currencyFormat(calc.getChargesForPercent(currentTaxPercent)) + "</ram:ActualAmount>\n" +
							"        <ram:Reason>" + XMLTools.encodeXML(calc.getChargeReasonForPercent(currentTaxPercent)) + "</ram:Reason>\n" +
							"        <ram:CategoryTradeTax>\n" +
							"          <ram:TypeCode>VAT</ram:TypeCode>\n" +
							"          <ram:CategoryCode>" + VATPercentAmountMap.get(currentTaxPercent).getCategoryCode() + "</ram:CategoryCode>\n" +
							"          <ram:RateApplicablePercent>" + vatFormat(currentTaxPercent) + "</ram:RateApplicablePercent>\n" +
							"        </ram:CategoryTradeTax>\n" +
							"      </ram:SpecifiedTradeAllowanceCharge>	\n";

				}
			}

		}

		if ((trans.getZFAllowances() != null) && (trans.getZFAllowances().length > 0)) {
			for (final BigDecimal currentTaxPercent : VATPercentAmountMap.keySet()) {
				if (calc.getAllowancesForPercent(currentTaxPercent).compareTo(BigDecimal.ZERO) != 0) {
					xml = xml + "	 <ram:SpecifiedTradeAllowanceCharge>\n" +
							"        <ram:ChargeIndicator>\n" +
							"          <udt:Indicator>false</udt:Indicator>\n" +
							"        </ram:ChargeIndicator>\n" +
							"        <ram:ActualAmount>" + currencyFormat(calc.getAllowancesForPercent(currentTaxPercent)) + "</ram:ActualAmount>\n" +
							"        <ram:Reason>" + XMLTools.encodeXML(calc.getAllowanceReasonForPercent(currentTaxPercent)) + "</ram:Reason>\n" +
							"        <ram:CategoryTradeTax>\n" +
							"          <ram:TypeCode>VAT</ram:TypeCode>\n" +
							"          <ram:CategoryCode>" + VATPercentAmountMap.get(currentTaxPercent).getCategoryCode() + "</ram:CategoryCode>\n" +
							"          <ram:RateApplicablePercent>" + vatFormat(currentTaxPercent) + "</ram:RateApplicablePercent>\n" +
							"        </ram:CategoryTradeTax>\n" +
							"      </ram:SpecifiedTradeAllowanceCharge>	\n";
				}
			}
		}


		if ((trans.getPaymentTerms() == null) && ((paymentTermsDescription != null) || (trans.getTradeSettlement() != null) || (hasDueDate))) {
			xml = xml + "<ram:SpecifiedTradePaymentTerms>\n";

			if (paymentTermsDescription != null) {
				xml = xml + "<ram:Description>" + paymentTermsDescription + "</ram:Description>\n";
			}

			if (trans.getTradeSettlement() != null) {
				for (final IZUGFeRDTradeSettlement payment : trans.getTradeSettlement()) {
					if ((payment != null) && (payment instanceof IZUGFeRDTradeSettlementDebit)) {
		//				xml += payment.getPaymentXML();
					}
				}
			}

			if (hasDueDate && (trans.getDueDate() != null)) {
				xml = xml + "				<ram:DueDateDateTime>" // $NON-NLS-2$
						+ DATE.udtFormat(trans.getDueDate())
						+ "</ram:DueDateDateTime>\n";// 20130704

			}
			xml = xml + "			</ram:SpecifiedTradePaymentTerms>\n";
		} else {
			xml = xml + buildPaymentTermsXml();
		}


		final String allowanceTotalLine = "<ram:AllowanceTotalAmount>" + currencyFormat(calc.getAllowancesForPercent(null)) + "</ram:AllowanceTotalAmount>";

		final String chargesTotalLine = "<ram:ChargeTotalAmount>" + currencyFormat(calc.getChargesForPercent(null)) + "</ram:ChargeTotalAmount>";

		xml = xml + "			<ram:SpecifiedTradeSettlementHeaderMonetarySummation>\n"
				+ "				<ram:LineTotalAmount>" + currencyFormat(calc.getTotal()) + "</ram:LineTotalAmount>\n"
				+ chargesTotalLine
				+ allowanceTotalLine
				+ "				<ram:TaxBasisTotalAmount>" + currencyFormat(calc.getTaxBasis()) + "</ram:TaxBasisTotalAmount>\n"
				// //
				// currencyID=\"EUR\"
				+ "				<ram:TaxTotalAmount currencyID=\"" + trans.getCurrency() + "\">"
				+ currencyFormat(calc.getGrandTotal().subtract(calc.getTaxBasis())) + "</ram:TaxTotalAmount>\n"
				+ "				<ram:GrandTotalAmount>" + currencyFormat(calc.getGrandTotal()) + "</ram:GrandTotalAmount>\n"
				// //
				// currencyID=\"EUR\"
				//+ "             <ram:TotalPrepaidAmount>" + currencyFormat(calc.getTotalPrepaid()) + "</ram:TotalPrepaidAmount>\n"
				//+ "				<ram:DuePayableAmount>" + currencyFormat(calc.getGrandTotal().subtract(calc.getTotalPrepaid())) + "</ram:DuePayableAmount>\n"
				+ "			</ram:SpecifiedTradeSettlementHeaderMonetarySummation>\n";
		if (trans.getInvoiceReferencedDocumentID() != null) {
			xml = xml + "   <ram:InvoiceReferencedDocument>\n"
					+ "       <ram:IssuerAssignedID>"
					+ XMLTools.encodeXML(trans.getInvoiceReferencedDocumentID()) + "</ram:IssuerAssignedID>\n";
			if (trans.getInvoiceReferencedIssueDate() != null) {
				xml += "<ram:FormattedIssueDateTime>"
						+ DATE.qdtFormat(trans.getInvoiceReferencedIssueDate())
						+ "</ram:FormattedIssueDateTime>\n";
			}
			xml += "   </ram:InvoiceReferencedDocument>\n";
		}

		xml = xml + "		</ram:ApplicableHeaderTradeSettlement>\n";
		// + " <IncludedSupplyChainTradeLineItem>\n"
		// + " <AssociatedDocumentLineDocument>\n"
		// + " <IncludedNote>\n"
		// + " <Content>Wir erlauben uns Ihnen folgende Positionen aus der Lieferung Nr.
		// 2013-51112 in Rechnung zu stellen:</Content>\n"
		// + " </IncludedNote>\n"
		// + " </AssociatedDocumentLineDocument>\n"
		// + " </IncludedSupplyChainTradeLineItem>\n";

		xml = xml + "	</rsm:SupplyChainTradeTransaction>\n"
				+ "</rsm:SCRDMCCBDACIOMessageStructure>";

		final byte[] zugferdRaw;
		try {
			zugferdRaw = xml.getBytes("UTF-8");

			zugferdData = XMLTools.removeBOM(zugferdRaw);
		} catch (final UnsupportedEncodingException e) {
			Logger.getLogger(OXPullProvider.class.getName()).log(Level.SEVERE, null, e);
		}
	}


	@Override
	public void setProfile(Profile p) {
		profile = p;
	}

	@Override
	public Profile getProfile() {
		return profile;
	}

	private String buildPaymentTermsXml() {

		final IZUGFeRDPaymentTerms paymentTerms = trans.getPaymentTerms();
		if (paymentTerms == null) {
			return "";
		}
		String paymentTermsXml = "<ram:SpecifiedTradePaymentTerms>";

		final IZUGFeRDPaymentDiscountTerms discountTerms = paymentTerms.getDiscountTerms();
		final Date dueDate = paymentTerms.getDueDate();
		if (dueDate != null && discountTerms != null && discountTerms.getBaseDate() != null) {
			throw new IllegalStateException(
					"if paymentTerms.dueDate is specified, paymentTerms.discountTerms.baseDate has not to be specified");
		}
		paymentTermsXml += "<ram:Description>" + paymentTerms.getDescription() + "</ram:Description>";
		if (dueDate != null) {
			paymentTermsXml += "<ram:DueDateDateTime>";
			paymentTermsXml += DATE.udtFormat(dueDate);
			paymentTermsXml += "</ram:DueDateDateTime>";
		}

		if (discountTerms != null) {
			paymentTermsXml += "<ram:ApplicableTradePaymentDiscountTerms>";
			final String currency = trans.getCurrency();
			final String basisAmount = currencyFormat(calc.getGrandTotal());
			paymentTermsXml += "<ram:BasisAmount currencyID=\"" + currency + "\">" + basisAmount + "</ram:BasisAmount>";
			paymentTermsXml += "<ram:CalculationPercent>" + discountTerms.getCalculationPercentage().toString()
					+ "</ram:CalculationPercent>";

			if (discountTerms.getBaseDate() != null) {
				final Date baseDate = discountTerms.getBaseDate();
				paymentTermsXml += "<ram:BasisDateTime>";
				paymentTermsXml += DATE.udtFormat(baseDate);
				paymentTermsXml += "</ram:BasisDateTime>";

				paymentTermsXml += "<ram:BasisPeriodMeasure unitCode=\"" + discountTerms.getBasePeriodUnitCode() + "\">"
						+ discountTerms.getBasePeriodMeasure() + "</ram:BasisPeriodMeasure>";
			}

			paymentTermsXml += "</ram:ApplicableTradePaymentDiscountTerms>";
		}

		paymentTermsXml += "</ram:SpecifiedTradePaymentTerms>";
		return paymentTermsXml;
	}

}
