package org.example.DomainLayer;

public class Rating
{
    int rating; //0 to 5
    String userID;

    public Rating(int rating, String userID)
    {
        this.rating = rating;
        this.userID = userID;
    }

    public int getRating()
    {
        return this.rating;
    }
}
