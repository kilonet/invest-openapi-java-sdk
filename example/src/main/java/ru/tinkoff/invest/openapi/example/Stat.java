package ru.tinkoff.invest.openapi.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Stat {

    private List<PortfolioItem> items;

    public Stat(List<PortfolioItem> items) {
        this.items = items.stream().map(it -> {
            PortfolioItem portfolioItem = new PortfolioItem();
            portfolioItem.geo = it.geo;
            portfolioItem.asset = it.asset;
            portfolioItem.name = it.name;
            portfolioItem.ticker = it.ticker;
            portfolioItem.price = it.price.divide(BigDecimal.valueOf(1000), RoundingMode.HALF_UP);
            return portfolioItem;
        }).collect(Collectors.toList());
    }

    public BigDecimal getSum() {
        BigDecimal sum = BigDecimal.ZERO;
        for (PortfolioItem item:items) {
            sum = item.price.add(sum);
        }
        return sum;
    }

    public Map<Asset, BigDecimal> getAssetStat() {
        Map<Asset, BigDecimal> stat = new HashMap<>();
        for (PortfolioItem item:items) {
            if (stat.containsKey(item.asset)) {
                stat.put(item.asset, stat.get(item.asset).add(item.price));
            } else {
                stat.put(item.asset, item.price);
            }
        }
        return stat;
    }

    public Map<Geo, BigDecimal> getGeoStat() {
        Map<Geo, BigDecimal> stat = new HashMap<>();
        for (PortfolioItem item:items) {
            if (stat.containsKey(item.geo)) {
                stat.put(item.geo, stat.get(item.geo).add(item.price));
            } else {
                stat.put(item.geo, item.price);
            }
        }
        return stat;
    }

    public Map<Asset, String> getAssetStatPercent() {
        BigDecimal sum = getSum();
        return getAssetStat().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, kv -> {
            BigDecimal v = kv.getValue().divide(sum, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);
            return v + "%";
        }));
    }

    public Map<Geo, String> getGeoStatPercent() {
        BigDecimal sum = getSum();
        return getGeoStat().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, kv -> {
            BigDecimal v = kv.getValue().divide(sum, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP);
            return v + "%";
        }));
    }

}
