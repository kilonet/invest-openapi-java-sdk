package ru.tinkoff.invest.openapi.example;

import ru.tinkoff.invest.openapi.MarketContext;
import ru.tinkoff.invest.openapi.OpenApi;
import ru.tinkoff.invest.openapi.model.rest.Candle;
import ru.tinkoff.invest.openapi.model.rest.CandleResolution;
import ru.tinkoff.invest.openapi.model.rest.Candles;
import ru.tinkoff.invest.openapi.model.rest.Currency;
import ru.tinkoff.invest.openapi.model.rest.InstrumentType;
import ru.tinkoff.invest.openapi.model.rest.MarketInstrumentList;
import ru.tinkoff.invest.openapi.model.rest.Portfolio;
import ru.tinkoff.invest.openapi.model.rest.PortfolioPosition;
import ru.tinkoff.invest.openapi.model.rest.SandboxRegisterRequest;
import ru.tinkoff.invest.openapi.model.rest.UserAccount;
import ru.tinkoff.invest.openapi.okhttp.OkHttpOpenApi;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

public class App {

    private static final List<String> BOND_ETFS = Arrays.asList("FXTP", "FXRU", "FXFA");

    private static final List<String> RU = Arrays.asList("SIBN TCSG OZON NVTK SBER GAZP MOEX AGRO PLZL TMOS MTSS FIVE AFLT RU000A100HE1 FXRU FXRL QIWI GMKN MAIL YNDX".split(" "));
    private static final List<String> US = Arrays.asList(("VTBA USD000UTSTOM FXIT TGRN LRN EPAM AMD AMZN GRMN INTC KO V MCD FXIM TSPV AAL MSFT FXTP " +
            "TECH " +
            "TSPX TBIO " +
            "TIPO " +
            "FXITSD000UTSTOM AAPL").split(" "));
    private static final List<String> EM = Arrays.asList("VTBE".split(" "));
    private static final List<String> DM = Arrays.asList("TEUS SNY FXFA FXDM TEUR FXDE EUR_RUB__TOM".split(" "));
    private static final List<String> CN = Arrays.asList("BABA BIDU FXCN".split(" "));
    static org.slf4j.Logger logger;

    public static void main(String[] args) {
        try {
            logger = initLogger();
        } catch (IOException ex) {
            System.err.println("При инициализации логгера произошла ошибка: " + ex.getLocalizedMessage());
            return;
        }

        final TradingParameters parameters;
        try {
            parameters = extractParams(args);
        } catch (IllegalArgumentException ex) {
            logger.error("Не удалось извлечь торговые параметры.", ex);
            return;
        }

        try (final OpenApi api = new OkHttpOpenApi(parameters.ssoToken, parameters.sandboxMode)) {
            logger.info("Создаём подключение... ");
            if (api.isSandboxMode()) {
                // ОБЯЗАТЕЛЬНО нужно выполнить регистрацию в "песочнице"
                api.getSandboxContext().performRegistration(new SandboxRegisterRequest()).join();
            }

            //play(api);


            portfolio(api);


        } catch (final Exception ex) {
            logger.error("Что-то пошло не так.", ex);
        }
    }

    private static void play(OpenApi api) throws InterruptedException, ExecutionException {
        MarketContext marketContext = api.getMarketContext();
        MarketInstrumentList marketInstrumentList = marketContext.getMarketStocks().get();
        Candles marketCandles = marketContext.getMarketCandles("BBG004730N88", OffsetDateTime.now().minusYears(1), OffsetDateTime.now(), CandleResolution.DAY).get().get();
        BigDecimal initial = new BigDecimal("1000000");
        BigDecimal singlePay = new BigDecimal("900000");
        int periodDays = 300;
        int total = 0;
        int totalBuyTimes = 0;
        BigDecimal totalMoneySpent = new BigDecimal("0");

        for (int i = 0; i < marketCandles.getCandles().size(); i = i + periodDays) {
            Candle candle = marketCandles.getCandles().get(i);
            BigDecimal currentPrice = candle.getO();
            if (singlePay.doubleValue() > currentPrice.doubleValue()) {
                int toBuy = singlePay.divide(currentPrice, RoundingMode.HALF_UP).intValue();
                total += toBuy;
                BigDecimal buySum = currentPrice.multiply(new BigDecimal(toBuy));
                initial = initial.subtract(buySum);
                totalMoneySpent = totalMoneySpent.add(buySum);
            }
            totalBuyTimes++;
        }
        Candle lastPrice = marketCandles.getCandles().get(marketCandles.getCandles().size() - 1);
        Candle firstPrice = marketCandles.getCandles().get(0);
        BigDecimal finalSum = (new BigDecimal(total)).multiply(lastPrice.getO());
        BigDecimal profit = finalSum.divide(totalMoneySpent, RoundingMode.UP);


        System.out.println("final sum:" + finalSum);
        System.out.println("total buy times:" + totalBuyTimes);
        System.out.println("total money spent:" + totalMoneySpent);
        System.out.println("profit: " + profit );
        System.out.println("last/1st price: " + lastPrice.getO().divide(firstPrice.getO(), RoundingMode.UP));
    }

    private static void portfolio(OpenApi api) throws InterruptedException, ExecutionException {
        List<UserAccount> accounts = api.getUserContext().getAccounts().get().getAccounts();

        BigDecimal usdPrice = api.getMarketContext().getMarketOrderbook("BBG0013HGFT4", 20).get().get().getLastPrice();
        BigDecimal eurPrice = api.getMarketContext().getMarketOrderbook("BBG0013HJJ31", 20).get().get().getLastPrice();

        List<PortfolioItem> allItems = new ArrayList<>();

        accounts.forEach(userAccount -> {
            System.out.println(userAccount.getBrokerAccountType());
            List<PortfolioItem> portfolioItems = new ArrayList<>();
            try {
                Portfolio portfolio = api.getPortfolioContext().getPortfolio(userAccount.getBrokerAccountId()).get();
                portfolioItems.addAll(portfolio.getPositions().stream().map(portfolioPosition -> portfolioItem(eurPrice, usdPrice, portfolioPosition)).collect(Collectors.toList()));

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            allItems.addAll(portfolioItems);

            Stat stat = new Stat(portfolioItems);

            System.out.println(stat.getSum());
            System.out.println(stat.getAssetStat());
            System.out.println(stat.getGeoStat());
            System.out.println(stat.getAssetStatPercent());
            System.out.println(stat.getGeoStatPercent());
        });

        PortfolioItem newBondItem = new PortfolioItem();
        newBondItem.asset = Asset.BONDS;
        newBondItem.price = BigDecimal.valueOf(500_000);
//            portfolioItems.add(newBondItem);


        Stat allStat = new Stat(allItems);

        System.out.println("ALL");

        System.out.println(allStat.getSum());
        System.out.println(allStat.getAssetStat());
        System.out.println(allStat.getGeoStat());
        System.out.println(allStat.getAssetStatPercent());
        System.out.println(allStat.getGeoStatPercent());

    }

    private static PortfolioItem portfolioItem(BigDecimal eurPrice, BigDecimal usdPrice, PortfolioPosition p) {
        BigDecimal rubPrice;
        BigDecimal pSum = p.getAveragePositionPrice().getValue().multiply(p.getBalance()).add(p.getExpectedYield().getValue());
        Currency pCurrency = p.getAveragePositionPrice().getCurrency();
        if (pCurrency == Currency.USD) {
            rubPrice = pSum.multiply(usdPrice);
        } else if (pCurrency == Currency.EUR) {
            rubPrice = pSum.multiply(eurPrice);
        } else if (pCurrency == Currency.RUB) {
            rubPrice = pSum;
        } else {
            throw new IllegalStateException();
        }
//        System.out.println(p.getName() + " " + p.getTicker() + " - " + rubPrice);
        PortfolioItem item = new PortfolioItem();
        item.name = p.getName();
        item.price = rubPrice;
        item.ticker = p.getTicker();
        item.asset = getAsset(p);
        item.geo = getGeo(p);
        return item;
    }

    private static Asset getAsset(PortfolioPosition p) {
        InstrumentType instrumentType = p.getInstrumentType();
        if (instrumentType == InstrumentType.STOCK) {
            return Asset.STOCKS;
        } else if (instrumentType == InstrumentType.ETF) {
            if (BOND_ETFS.contains(p.getTicker())) {
                return Asset.BONDS;
            } else {
                return Asset.STOCKS;
            }
        } else if (instrumentType == InstrumentType.BOND) {
            return Asset.BONDS;
        } else if (instrumentType == InstrumentType.CURRENCY) {
            return Asset.CURRENCY;
        }
        throw new IllegalStateException();
    }

    private static Geo getGeo(PortfolioPosition p) {
        String ticker = p.getTicker();
        if (RU.contains(ticker)) {
            return Geo.RU;
        }
        if (US.contains(ticker)) {
            return Geo.US;
        }
        if (EM.contains(ticker)) {
            return Geo.EM;
        }
        if (DM.contains(ticker)) {
            return Geo.DM;
        }
        if (CN.contains(ticker)) {
            return Geo.CN;
        }
        throw new IllegalStateException("geo unknown: " + ticker);
    }

    private static org.slf4j.Logger initLogger() throws IOException {
        final var logManager = LogManager.getLogManager();
        final var classLoader = App.class.getClassLoader();

        try (final InputStream input = classLoader.getResourceAsStream("logging.properties")) {

            if (input == null) {
                throw new FileNotFoundException();
            }

            Files.createDirectories(Paths.get("./logs"));
            logManager.readConfiguration(input);
        }

        return org.slf4j.LoggerFactory.getLogger(App.class);
    }

    private static TradingParameters extractParams(final String[] args) throws IllegalArgumentException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Не передан авторизационный токен!");
        } else if (args.length == 1) {
            throw new IllegalArgumentException("Не передан исследуемый тикер!");
        } else if (args.length == 2) {
            throw new IllegalArgumentException("Не передан разрешающий интервал свечей!");
        } else if (args.length == 3) {
            throw new IllegalArgumentException("Не передан признак использования песочницы!");
        } else {
            return TradingParameters.fromProgramArgs(args[0], args[1], args[2], args[3]);
        }
    }

}
