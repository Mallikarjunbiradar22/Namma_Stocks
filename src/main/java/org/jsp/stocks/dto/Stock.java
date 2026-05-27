package org.jsp.stocks.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Stock {
	@Id
	private String ticker;
	private String companyName;
	private double price;
	private double changes;
	private double quantity;

	public String getTicker() {
		return ticker;
	}

	public void setTicker(String ticker) {
		this.ticker = ticker;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public double getPrice() {
		return price;
	}

	public void setPrice(double price) {
		this.price = price;
	}

	public double getChanges() {
		return changes;
	}

	public void setChanges(double changes) {
		this.changes = changes;
	}

	public double getQuantity() {
		return quantity;
	}

	public void setQuantity(double quantity) {
		this.quantity = quantity;
	}

	@Override
	public String toString() {
		return "Stock [ticker=" + ticker + ", companyName=" + companyName + ", price=" + price + ", changes=" + changes
				+ ", quantity=" + quantity + "]";
	}
}
