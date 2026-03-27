package papervalidation.util;

/**
 * Vincenty 측지 공식 기반 거리/방위 계산 유틸리티.
 * WGS84 타원체 사용.
 */
public final class VincentyUtils {

    private static final double A = 6378137.0;           // 장반경 (m)
    private static final double F = 1.0 / 298.257223563; // 편평률
    private static final double B = A * (1 - F);         // 단반경

    private VincentyUtils() {
    }

    /**
     * 두 WGS84 좌표 간 거리(m)를 계산한다.
     */
    public static double distance(double lat1Deg, double lon1Deg, double lat2Deg, double lon2Deg) {
        double lat1 = Math.toRadians(lat1Deg);
        double lon1 = Math.toRadians(lon1Deg);
        double lat2 = Math.toRadians(lat2Deg);
        double lon2 = Math.toRadians(lon2Deg);

        double U1 = Math.atan((1 - F) * Math.tan(lat1));
        double U2 = Math.atan((1 - F) * Math.tan(lat2));
        double sinU1 = Math.sin(U1), cosU1 = Math.cos(U1);
        double sinU2 = Math.sin(U2), cosU2 = Math.cos(U2);

        double L = lon2 - lon1;
        double lambda = L;
        double lambdaPrev;

        double sinSigma, cosSigma, sigma, sinAlpha, cos2Alpha, cos2SigmaM;

        int iterLimit = 100;
        do {
            double sinLambda = Math.sin(lambda);
            double cosLambda = Math.cos(lambda);

            sinSigma = Math.sqrt(
                    Math.pow(cosU2 * sinLambda, 2) +
                    Math.pow(cosU1 * sinU2 - sinU1 * cosU2 * cosLambda, 2)
            );
            if (sinSigma == 0) return 0; // 동일 지점

            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cos2Alpha = 1 - sinAlpha * sinAlpha;
            cos2SigmaM = (cos2Alpha != 0) ? cosSigma - 2 * sinU1 * sinU2 / cos2Alpha : 0;

            double C = F / 16.0 * cos2Alpha * (4 + F * (4 - 3 * cos2Alpha));
            lambdaPrev = lambda;
            lambda = L + (1 - C) * F * sinAlpha *
                    (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));

        } while (Math.abs(lambda - lambdaPrev) > 1e-12 && --iterLimit > 0);

        double uSq = cos2Alpha * (A * A - B * B) / (B * B);
        double bigA = 1 + uSq / 16384.0 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
        double bigB = uSq / 1024.0 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));

        cos2SigmaM = (cos2Alpha != 0) ? cosSigma - 2 * sinU1 * sinU2 / cos2Alpha : 0;
        double deltaSigma = bigB * sinSigma * (cos2SigmaM + bigB / 4.0 *
                (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                bigB / 6.0 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));

        return B * bigA * (sigma - deltaSigma);
    }
}