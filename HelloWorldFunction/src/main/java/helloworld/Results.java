package helloworld;



import java.util.List;

public class Results {
    public Results() {
    }

    //    private DataObject dataObject;
    private List<Entry> data;
    private StatusObject status;
    private Coin coin;

    public List<Entry> getData() {
        return data;
    }

    public void setData(List<Entry> data) {
        this.data = data;
    }

    public StatusObject getStatus() {
        return status;
    }

    public void setStatus(StatusObject status) {
        this.status = status;
    }

    public Coin getCoin() {
        return coin;
    }

    public void setCoin(Coin coin) {
        this.coin = coin;
    }
}