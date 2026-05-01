package org.example.DomainLayer;

import java.util.UUID;

public class Rating
{
    int rating; //0 to 5
    UUID userID;

    public Rating(int rating, UUID userID)
    {
        this.rating = rating;
        this.userID = userID;
    }

    public int getRating()
    {
        return this.rating;
    }
}
