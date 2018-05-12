package com.cloudcomputing.samza.ny_cabs;

public class Driver {
    private Double driverId;
    private Double blockId;
    private Double latitude;
    private Double longitude;
    private String gender;
    private Double rating;
    private Double salary;
    private String status;
    private Double score = null;

    public Double getDriverId() {
        return driverId;
    }

    public void setDriverId(Double driverId) {
        this.driverId = driverId;
    }

    public void setBlockId(Double blockId) {
        this.blockId = blockId;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Double getSalary() {
        return salary;
    }

    public void setSalary(Double salary) {
        this.salary = salary;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double clientDriverDistance(Double driverLat, Double driverLon, Double clientLat, Double clientLon) {
        return Math.sqrt(Math.pow((driverLat - clientLat), 2) + Math.pow((driverLon - clientLon), 2));
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getScore() {
        return score;
    }

    public Double matchScore(Double clientLat, Double clientLon, String genderPref) {

        Double genderScore;
        if (genderPref.equals("N") || genderPref.equals(gender)) {
            genderScore = 1.0;
        } else {
            genderScore = 0.0;
        }

        Double distanceScore =  Math.exp(-1 * clientDriverDistance(latitude, longitude, clientLat, clientLon));
        Double ratingScore = rating / 5.0;
        Double salaryScore = 1 - salary / 100.0;

        return distanceScore * 0.4 + genderScore * 0.1 + ratingScore * 0.3 + salaryScore * 0.2;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("driverId: ").append(driverId).append(" ")
        .append("blockId: ").append(blockId).append(" ")
        .append("latitude: ").append(latitude).append(" ")
        .append("longitude: ").append(longitude).append(" ")
        .append("score: ").append(score).append(" ")
        .append("status: ").append(status).append(" ")
        .append("rating: ").append(rating).append(" ")
        .append("salary: ").append(salary).append(" ");

        return sb.toString();
    }
}
