The file contains short description of the LogAnalyzer tool. 

What is LogAnalyzer:
------------------------
    The LogAnalyzer tool process different kinds of log file and extract information from each log. 
The information is saved to mysql db. There are different kinds of information such as (MethodCount, ActivityCount,
MediaCount, ErrorMessageCount, LiveStreamDTO ) are extracted from the log files.
Now the LogAnalyzer have following features:
    1. ActivityCount
    2. ErrorMessageCount
    3. ListStat
    4. LiveStream
    5. MediaCount
    6. MethodCount

Some tables Specification:
-----------------------------
    1. Name : ActivityMethodMap
    2. Columns : id, activity, method
    3. Description : The table contains "method" to "activity" mapping. It shows the meaning or activity of the user
from its corresponding method. A method may depict multiple meaning or activity.
    4. Table Specification :
        Table Create Schema:
            CREATE TABLE ActivityMethodMap(
                    id bigint(20) auto_increment,
                    activity VARCHAR(100) NOT NULL,
                    method VARCHAR(100) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY (activity, method)
                    );
        Sample data :
            INSERT INTO ActivityMethodMap (activity, method) VALUES("Account created", "addProfile");
            INSERT INTO ActivityMethodMap (activity, method) VALUES("Friend request sent", "addContact");
            INSERT INTO ActivityMethodMap (activity, method) VALUES("Connection created", "addContactAutomatic");
            INSERT INTO ActivityMethodMap (activity, method) VALUES("Friend request accepted", "acceptFriendRequest");
            INSERT INTO ActivityMethodMap (activity, method) VALUES("Connection created", "acceptFriendRequest");
            INSERT INTO ActivityMethodMap (activity, method) VALUES("Friend removed", "unfriend");
            INSERT INTO ActivityMethodMap (activity, method) VALUES("Cover image updated", "updateCoverImage");
            INSERT INTO ActivityMethodMap (activity, method) VALUES("Profile image updated", "updateProfileImage");
            INSERT INTO ActivityMethodMap (activity, method) VALUES("User profile info updated", "updateUserProfile");

    1. Name : Setting (The use case of the table is to enable revisiting different features)
    2. Columns : name, value
    3. Description of "name" field's value :
            1. threshold_days : How many maximum back days from "revisit_time" will we consider to revisit.
            2. revisit_time : From which time to current time the revisit will run.
            3. revisit_features : which features will by revisited.
            N.B -> "revisit_time" and "revisit_features" are mandatory setting names to revisit features. Default value of "threadhold_days" is 0.
   
   4. Table Specification :
        Table create schema:        
            CREATE TABLE Setting(
            name VARCHAR(100) NOT NULL,
            value VARCHAR(100),
            PRIMARY KEY (name)
            );
        
        Some sample data:

            INSERT INTO Setting (name, value) VALUES ("threshold_days", "2");
            INSERT INTO Setting (name, value) VALUES ("revisit_time", "20160817");
            INSERT INTO Setting (name, value) VALUES ("revisit_features", "MediaCount,ActivityCount");