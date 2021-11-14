package uk.co.hexillium.rhul.compsoc.persistence.entities;

import java.time.OffsetDateTime;

public class MembershipDetail {
    int transaction_id;
    String purchaser;
    String textbox6;
    String card_number;
    String shop_name;
    int qty;
    OffsetDateTime purchase_date;
    String type;

    public MembershipDetail(int transaction_id, String purchaser, String textbox6, String card_number, String shop_name, int qty, OffsetDateTime purchase_date, String type) {
        this.transaction_id = transaction_id;
        this.purchaser = purchaser;
        this.textbox6 = textbox6;
        this.card_number = card_number;
        this.shop_name = shop_name;
        this.qty = qty;
        this.purchase_date = purchase_date;
        this.type = type;
    }

    public int getTransaction_id() {
        return transaction_id;
    }

    public String getPurchaser() {
        return purchaser;
    }

    public String getTextbox6() {
        return textbox6;
    }

    public String getCard_number() {
        return card_number;
    }

    public String getShop_name() {
        return shop_name;
    }

    public int getQty() {
        return qty;
    }

    public String getType() {
        return type;
    }

    public OffsetDateTime getPurchase_date() {
        return purchase_date;
    }
}

