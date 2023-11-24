package helloworld;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Quote {

    public Quote() {
    }

    @JsonProperty("BTC")
    private Quote2 BTC;

    @JsonProperty("USD")
    private Quote1 USD;


    public Quote1 getUSD() {
        return USD;
    }

    public void setUSD(Quote1 USD) {
        this.USD = USD;
    }

    public Quote2 getBTC() {
        return BTC;
    }

    public void setBTC(Quote2 BTC) {
        this.BTC = BTC;
    }


}
