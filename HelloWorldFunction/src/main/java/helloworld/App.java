package helloworld;


import com.amazonaws.regions.Regions;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.InvokeRequest;
import com.amazonaws.services.lambda.model.InvokeResult;
import com.amazonaws.services.lambda.model.ServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.protocol.Resultset;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handler for requests to Lambda function.
 */                                     
public class App implements RequestHandler<Object, String> {

    private static SessionFactory sessionFactory;
    private static final String apiKey = "******************************";

    public String handleRequest(Object thing, Context context) {

        //establishes hibernate connection
        Session sss = getSessionFromFactory();

        //pre-reqs for CoinBase API
        String uri = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/listings/latest";
        List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        parameters.add(new BasicNameValuePair("start","1"));
        parameters.add(new BasicNameValuePair("limit","7"));
        parameters.add(new BasicNameValuePair("convert","USD"));

        try {
            //calls API and parses JSON results
            String result = makeAPICall(uri, parameters);
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
            mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);
            mapper.enable(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT);
            mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
            mapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
            mapper.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            Results root = mapper.readValue(result, Results.class);

            //Hibernate prep stuff
            Transaction transaction = sss.beginTransaction();
            CurrencyEntity currencyEntity = new CurrencyEntity();
            CurrencyEntity secondCoin = new CurrencyEntity();
            int ct = 1;

            //uses hibernate to get data from JSON results and enter it into DB
            for (int i = 0; i < root.getData().size(); i++){
                Entry node1 = root.getData().get(i);
                currencyEntity.setName(node1.getName());
                currencyEntity.setSymbol(node1.getSymbol());
                currencyEntity.setPrice(node1.getQuote().getUSD().getPrice());

                if (!Objects.equals(sss.get(CurrencyEntity.class, ct), null)){
                    secondCoin = sss.get(CurrencyEntity.class, ct);
                    for (int j = 0; j < root.getData().size(); j ++) {
                       if (Objects.equals(currencyEntity.getSymbol(), secondCoin.getSymbol())) {
                        secondCoin.setPrice(currencyEntity.getPrice());
                        sss.update(secondCoin);
                        }
                    }

                }
            else {
                    sss.merge(currencyEntity);
                }
                ct++;
            }
            sss.flush();
            transaction.commit();
        }
        //in case api call fails
        catch (IOException e) {
            System.out.println("Error: cannont access content - " + e.toString());
        } catch (URISyntaxException e) {
            System.out.println("Error: Invalid URL " + e.toString());
        }
        //close hibernate stuff
        if (sss.isOpen()){
            sss.close();
        }
            if (sessionFactory.isOpen()){
            sessionFactory.close();
        }
        //this triggers second lambda
        invoke();
        return null;
    }
    public static String makeAPICall(String uri, List<NameValuePair> parameters)
        //CoinBase API
        throws URISyntaxException, IOException {
        String response_content = "";
        URIBuilder query = new URIBuilder(uri);
        query.addParameters(parameters);
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet request = new HttpGet(query.build());
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        request.addHeader("X-CMC_PRO_API_KEY", apiKey);
        CloseableHttpResponse response = client.execute(request);
        try {
            System.out.println(response.getEntity());
            HttpEntity entity = response.getEntity();
            response_content = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } finally {
            response.close();
        }
        return response_content;
    }
    public static SessionFactory getFactoryFromConfig() {
        if (sessionFactory == null) {
            sessionFactory = new Configuration().configure().buildSessionFactory();
            return sessionFactory;
        }
        return sessionFactory;
    }
    public static Session getSessionFromFactory(){
        sessionFactory = getFactoryFromConfig();
        return sessionFactory.openSession();
    }

//    This was used in an earlier version of the program. Obsolete now.
//    public static void truncateTable(){
//
//        String userName = "admin";
//        String passWord = "adminadmin";
//        String sqlUrl = "jdbc:mysql://*****************************.rds.amazonaws.com:3306/Crypto_Stuff";
//        String trunk = "truncate table currency";
//
//        try{
//            Connection connection = DriverManager.getConnection(sqlUrl, userName, passWord);
//            Statement statement = connection.createStatement();
//            statement.executeUpdate(trunk);
//            statement.close();
//            connection.close();
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }


    public void invoke(){
        //this triggers other lambda
        String functionName = "arn:aws:lambda:****************************************************";
        InvokeRequest invokeRequest = new InvokeRequest().withFunctionName(functionName);
        InvokeResult invokeResult = null;

        try {
            AWSLambda awsLambda = AWSLambdaClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1).build();
            invokeResult = awsLambda.invoke(invokeRequest);

        } catch (ServiceException e) {
            System.out.println(e);
        }
        System.out.println(Objects.requireNonNull(invokeResult).getStatusCode());

        }
    }



