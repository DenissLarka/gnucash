/**
 * GnucashTransactionImpl.java
 * License: GPLv3 or later
 * Created on 13.05.2005
 * (c) 2005 by "Wolschon Softwaredesign und Beratung".
 * -----------------------------------------------------------
 * major Changes:
 * 13.05.2005 - initial version
 * ...
 */
package org.gnucash.read.impl;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.gnucash.generated.GncTransaction;
import org.gnucash.generated.ObjectFactory;
import org.gnucash.generated.Slot;
import org.gnucash.generated.SlotValue;
import org.gnucash.generated.SlotsType;
import org.gnucash.numbers.FixedPointNumber;
import org.gnucash.read.GnucashAccount;
import org.gnucash.read.GnucashFile;
import org.gnucash.read.GnucashInvoice;
import org.gnucash.read.GnucashTransaction;
import org.gnucash.read.GnucashTransactionSplit;

/**
 * created: 13.05.2005 <br/>
 * Implementation of GnucashTransaction that uses JWSDP.
 *
 * @author <a href="mailto:Marcus@Wolschon.biz">Marcus Wolschon</a>
 */
public class GnucashTransactionImpl extends GnucashObjectImpl implements GnucashTransaction {

	/**
	 * the JWSDP-object we are facading.
	 */
	private final GncTransaction jwsdpPeer;

	/**
	 * The file we belong to.
	 */
	private final GnucashFile file;

	/**
	 * Create a new Transaction, facading a JWSDP-transaction.
	 *
	 * @param peer    the JWSDP-object we are facading.
	 * @param gncFile the file to register under
	 * @see #jwsdpPeer
	 */
	public GnucashTransactionImpl(final GncTransaction peer, final GnucashFile gncFile) {
		super((peer.getTrnSlots() == null) ? new ObjectFactory().createSlotsType() : peer.getTrnSlots(), gncFile);
		if (peer.getTrnSlots() == null) {
			peer.setTrnSlots(getSlots());
		}

		if (peer == null) {
			throw new IllegalArgumentException("null jwsdpPeer given");
		}

		if (gncFile == null) {
			throw new IllegalArgumentException("null file given");
		}

		jwsdpPeer = peer;
		file = gncFile;

		for (GnucashInvoice invoice : getInvoices()) {
			invoice.addTransaction(this);
		}

	}

	/**
	 * @see GnucashTransaction#isBalanced()
	 */
	public boolean isBalanced() {

		return getBalance().equals(new FixedPointNumber());

	}

	/**
	 * @return "ISO4217" for a currency "FUND" or a fond,...
	 * @see GnucashAccount#getCurrencyNameSpace()
	 */
	public String getCurrencyNameSpace() {
		return jwsdpPeer.getTrnCurrency().getCmdtySpace();
	}

	/**
	 * @see GnucashAccount#getCurrencyID()
	 */
	public String getCurrencyID() {
		return jwsdpPeer.getTrnCurrency().getCmdtyId();
	}

	/**
	 * The result is in the currency of the transaction.
	 *
	 * @return the balance of the sum of all splits
	 * @see GnucashTransaction#getBalance()
	 */
	public FixedPointNumber getBalance() {

		FixedPointNumber fp = new FixedPointNumber();

		for (GnucashTransactionSplit split : getSplits()) {
			fp.add(split.getValue());
		}

		return fp;
	}

	/**
	 * The result is in the currency of the transaction.
	 *
	 * @see GnucashTransaction#getBalanceFormatet()
	 */
	public String getBalanceFormatet() {
		return getCurrencyFormat().format(getBalance());
	}

	/**
	 * The result is in the currency of the transaction.
	 *
	 * @see GnucashTransaction#getBalanceFormatet(java.util.Locale)
	 */
	public String getBalanceFormatet(final Locale loc) {

		NumberFormat cf = NumberFormat.getInstance(loc);
		if (getCurrencyNameSpace().equals(GnucashAccount.CURRENCYNAMESPACE_CURRENCY)) {
			cf.setCurrency(Currency.getInstance(getCurrencyID()));
		} else {
			cf.setCurrency(null);
		}

		return cf.format(getBalance());
	}

	/**
	 * The result is in the currency of the transaction.
	 *
	 * @throws NumberFormatException if the input is not valid
	 * @see GnucashTransaction#getNegatedBalance()
	 */
	public FixedPointNumber getNegatedBalance() throws NumberFormatException {
		return getBalance().multiply(new FixedPointNumber("-100/100"));
	}

	/**
	 * The result is in the currency of the transaction.
	 *
	 * @see GnucashTransaction#getNegatedBalanceFormatet()
	 */
	public String getNegatedBalanceFormatet() throws NumberFormatException {
		return getCurrencyFormat().format(getNegatedBalance());
	}

	/**
	 * The result is in the currency of the transaction.
	 *
	 * @see GnucashTransaction#getNegatedBalanceFormatet(java.util.Locale)
	 */
	public String getNegatedBalanceFormatet(final Locale loc) throws NumberFormatException {
		NumberFormat cf = NumberFormat.getInstance(loc);
		if (getCurrencyNameSpace().equals(GnucashAccount.CURRENCYNAMESPACE_CURRENCY)) {
			cf.setCurrency(Currency.getInstance(getCurrencyID()));
		} else {
			cf.setCurrency(null);
		}

		return cf.format(getNegatedBalance());
	}

	/**
	 * @see GnucashTransaction#getId()
	 */
	public String getId() {
		return jwsdpPeer.getTrnId().getValue();
	}

	/**
	 * @return the invoices this transaction belongs to (not payments but the transaction belonging to handing out the invoice)
	 */
	public Collection<GnucashInvoice> getInvoices() {
		Collection<String> invoiceIDs = getInvoiceIDs();
		List<GnucashInvoice> retval = new ArrayList<GnucashInvoice>(invoiceIDs.size());

		for (String invoiceID : invoiceIDs) {

			GnucashInvoice invoice = file.getInvoiceByID(invoiceID);
			if (invoice == null) {
				System.err.println(
						"No invoice with id='"
								+ invoiceID
								+ "' for transaction '"
								+ getId()
								+ "' described '"
								+ getDescription()
								+ "'");
			} else {
				retval.add(invoice);
			}

		}

		return retval;
	}

	/**
	 * @return the invoices this transaction belongs to (not payments but the transaction belonging to handing out the invoice)
	 */

	@SuppressWarnings("unchecked")
	public Collection<String> getInvoiceIDs() {

		List<String> retval = new LinkedList<String>();

		SlotsType slots = jwsdpPeer.getTrnSlots();
		if (slots == null) {
			return retval;
		}

		for (Slot slot : (List<Slot>) slots.getSlot()) {
			if (!slot.getSlotKey().equals("gncInvoice")) {
				continue;
			}

			SlotValue slotvalue = slot.getSlotValue();

			Slot subslot = (Slot) slotvalue.getContent().get(0);
			if (!subslot.getSlotKey().equals("invoice-guid")) {
				continue;
			}

			if (!subslot.getSlotValue().getType().equals("guid")) {
				continue;
			}

			retval.add((String) subslot.getSlotValue().getContent().get(0));

		}

		return retval;
	}

	/**
	 * @see GnucashTransaction#getDescription()
	 */
	public String getDescription() {
		return jwsdpPeer.getTrnDescription();
	}

	/**
	 * @see GnucashTransaction#getGnucashFile()
	 */
	@Override
	public GnucashFile getGnucashFile() {
		return file;
	}

	/**
	 * @see #getSplits()
	 */
	protected List<GnucashTransactionSplit> mySplits = null;

	/**
	 * @param impl the split to add to mySplits
	 */
	@SuppressWarnings("unchecked")
	protected void addSplit(final GnucashTransactionSplitImpl impl) {
		if (!jwsdpPeer.getTrnSplits().getTrnSplit().contains(impl.getJwsdpPeer())) {
			jwsdpPeer.getTrnSplits().getTrnSplit().add(impl.getJwsdpPeer());
		}

		Collection<GnucashTransactionSplit> splits = getSplits();
		if (!splits.contains(impl)) {
			splits.add(impl);
		}

	}

	/**
	 * @see GnucashTransaction#getSplitsCount()
	 */
	public int getSplitsCount() {
		return getSplits().size();
	}

	/**
	 * @see GnucashTransaction#getSplitByID(java.lang.String)
	 */
	public GnucashTransactionSplit getSplitByID(final String id) {
		for (GnucashTransactionSplit split : getSplits()) {
			if (split.getId().equals(id)) {
				return split;
			}

		}
		return null;
	}

	/**
	 * @see GnucashTransaction#getFirstSplit()
	 */
	public GnucashTransactionSplit getFirstSplit() {
		return getSplits().iterator().next();
	}

	/**
	 * @see GnucashTransaction#getSecondSplit()
	 */
	public GnucashTransactionSplit getSecondSplit() {
		Iterator<GnucashTransactionSplit> iter = getSplits().iterator();
		iter.next();
		return iter.next();
	}

	/**
	 * @see GnucashTransaction#getSplits()
	 */
	@SuppressWarnings("unchecked")
	public List<GnucashTransactionSplit> getSplits() {
		if (mySplits == null) {
			List<GncTransaction.TrnSplits.TrnSplit> jwsdpSplits = jwsdpPeer.getTrnSplits().getTrnSplit();

			mySplits = new ArrayList<GnucashTransactionSplit>(jwsdpSplits.size());
			for (GncTransaction.TrnSplits.TrnSplit element : jwsdpSplits) {

				mySplits.add(createSplit(element));
			}
		}
		return mySplits;
	}

	/**
	 * Create a new split for a split found in the jaxb-data.
	 *
	 * @param element the jaxb-data
	 * @return the new split-instance
	 */
	protected GnucashTransactionSplitImpl createSplit(final GncTransaction.TrnSplits.TrnSplit element) {
		return new GnucashTransactionSplitImpl(element, this);
	}

	/**
	 * @see GnucashTransaction#getDateEntered()
	 */
	protected static final DateTimeFormatter DATE_ENTERED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

	/**
	 * @see GnucashTransaction#getDateEntered()
	 */
	protected ZonedDateTime dateEntered;

	/**
	 * @see GnucashTransaction#getDateEntered()
	 */
	public ZonedDateTime getDateEntered() {
		if (dateEntered == null) {
			String s = jwsdpPeer.getTrnDateEntered().getTsDate();
			try {
				//"2001-09-18 00:00:00 +0200"
				dateEntered = ZonedDateTime.parse(s, DATE_ENTERED_FORMAT);
			} catch (Exception e) {
				IllegalStateException ex =
						new IllegalStateException(
								"unparsable date '" + s + "' in transaction!");
				ex.initCause(e);
				throw ex;
			}
		}

		return dateEntered;
	}

	/**
	 * format of the dataPosted-field in the xml(jwsdp)-file.
	 */
	private static final DateTimeFormatter DATE_POSTED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");

	/**
	 * @see GnucashTransaction#getDatePosted()
	 */
	protected ZonedDateTime datePosted;

	/**
	 * The Currency-Format to use if no locale is given.
	 */
	protected NumberFormat currencyFormat;

	/**
	 * The Currency-Format to use if no locale is given.
	 *
	 * @return default currency-format with the transaction's
	 * currency set
	 */
	protected NumberFormat getCurrencyFormat() {
		if (currencyFormat == null) {
			currencyFormat = NumberFormat.getCurrencyInstance();
			if (getCurrencyNameSpace().equals(GnucashAccount.CURRENCYNAMESPACE_CURRENCY)) {
				currencyFormat.setCurrency(Currency.getInstance(getCurrencyID()));
			} else {
				currencyFormat = NumberFormat.getInstance();
			}

		}
		return currencyFormat;
	}

	/**
	 * @see GnucashTransaction#getDatePostedFormatted()
	 */
	public String getDatePostedFormatted() {
		return DateFormat.getDateInstance().format(getDatePosted());
	}

	/**
	 * @see GnucashTransaction#getDatePosted()
	 */
	public ZonedDateTime getDatePosted() {
		if (datePosted == null) {
			String s = jwsdpPeer.getTrnDatePosted().getTsDate();
			try {
				//"2001-09-18 00:00:00 +0200"
				datePosted = ZonedDateTime.parse(s, DATE_POSTED_FORMAT);
			} catch (Exception e) {
				IllegalStateException ex =
						new IllegalStateException(
								"unparsable date '" + s + "' in transaction with id='"
										+ getId()
										+ "'!");
				ex.initCause(e);
				throw ex;
			}
		}

		return datePosted;
	}

	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append("[GnucashTransactionImpl:");
		buffer.append(" id: ");
		buffer.append(getId());
		buffer.append(" description: ");
		buffer.append(getDescription());
		buffer.append(" #splits: ");
		buffer.append(mySplits.size());
		buffer.append(" dateEntered: ");
		try {
			buffer.append(DateFormat.getDateTimeInstance().format(getDateEntered()));
		} catch (Exception e) {
			e.printStackTrace();
			buffer.append("ERROR '" + e.getMessage() + "'");

		}
		buffer.append("]");
		return buffer.toString();
	}

	/**
	 * sorts primarily on the date the transaction happened
	 * and secondarily on the date it was entered.
	 *
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(final GnucashTransaction o) {

		GnucashTransaction other = o;

		try {
			int compare = other.getDatePosted().compareTo(getDatePosted());
			if (compare != 0) {
				return compare;
			}

			return other.getDateEntered().compareTo(getDateEntered());
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	/**
	 * @return the JWSDP-object we are facading.
	 */
	public GncTransaction getJwsdpPeer() {
		return jwsdpPeer;
	}

	public String getTransactionNumber() {
		return getJwsdpPeer().getTrnNum();
	}
}
