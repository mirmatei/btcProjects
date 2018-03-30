import com.jayway.restassured.RestAssured;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

class FeeCalculator {

    public static final String FASTEST = "fastestFee";
    public static final String HALFHOUR = "halfHourFee";
    public static final String HOUR = "hourFee";

    public static long extractOptimalFee(String feeType) {
        // default fee = 50 satoshi/byte
        int aux = 50;
        RequestSpecification httprequest = RestAssured.given();
        Response request = httprequest.get("https://bitcoinfees.earn.com/api/v1/fees/recommended");
        if ((request.statusCode() == 200)) {

            System.out.println("Retrieved fee estiamtes from API: " + request.asString());
            System.out.println("Using " + feeType);
            if (feeType.equalsIgnoreCase("fastestFee")) {
                aux = request.jsonPath().get("fastestFee");
            } else if (feeType.equalsIgnoreCase("halfHourFee")) {
                aux = request.jsonPath().get("halfHourFee");
            } else if (feeType.equalsIgnoreCase("hourFee")) {
                aux = request.jsonPath().get("hourFee");
            }
        } else {
            System.out.println("Estimate Fee API is not reachable, using default fee 50 satoshi/byte");
        }

        return (long) (aux * 1000);
    }


}

