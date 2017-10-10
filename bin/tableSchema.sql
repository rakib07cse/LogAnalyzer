
CREATE TABLE analytics_activity_count (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  activity varchar(100) NOT NULL,
  time bigint(20) NOT NULL,
  count bigint(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY key_unique (activity,time)
);

CREATE TABLE analytics_error_message_count (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  hashcode bigint(20) NOT NULL,
  type varchar(20) NOT NULL,
  message varchar(1000) DEFAULT NULL,
  time bigint(20) NOT NULL,
  count bigint(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY key_unique (hashcode,type,time)
);

CREATE TABLE analytics_media_count (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  type varchar(20) NOT NULL,
  time bigint(20) NOT NULL,
  count bigint(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY key_unique (type,time)
);

CREATE TABLE analytics_method_count (
  id bigint(20) NOT NULL AUTO_INCREMENT,
  method varchar(100) NOT NULL,
  time bigint(20) NOT NULL,
  count bigint(20) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY key_unique (method,time)
);


CREATE TABLE analytics_activity_method_map(
id bigint(20) auto_increment,
activity VARCHAR(100) NOT NULL,
method VARCHAR(100) NOT NULL,
PRIMARY KEY (id),
UNIQUE KEY (activity, method)
);

INSERT INTO analytics_activity_method_map (activity, method) VALUES("Account created", "addProfile");
INSERT INTO analytics_activity_method_map (activity, method) VALUES("Friend request sent", "addContact");
INSERT INTO analytics_activity_method_map (activity, method) VALUES("Connection created", "addContactAutomatic");
INSERT INTO analytics_activity_method_map (activity, method) VALUES("Friend request accepted", "acceptFriendRequest");
INSERT INTO analytics_activity_method_map (activity, method) VALUES("Connection created", "acceptFriendRequest");
INSERT INTO analytics_activity_method_map (activity, method) VALUES("Friend removed", "unfriend");
INSERT INTO analytics_activity_method_map (activity, method) VALUES("Cover image updated", "updateCoverImage");
INSERT INTO analytics_activity_method_map (activity, method) VALUES("Profile image updated", "updateProfileImage");
INSERT INTO analytics_activity_method_map (activity, method) VALUES("User profile info updated", "updateUserProfile");


CREATE TABLE analytics_settings(
name VARCHAR(100) NOT NULL,
value VARCHAR(100),
PRIMARY KEY (name)
);

INSERT INTO analytics_settings (name, value) VALUES ("threshold_days", "2");
INSERT INTO analytics_settings (name, value) VALUES ("revisit_time", "20170525");
INSERT INTO analytics_settings (name, value) VALUES ("revisit_features", "MediaCount,ActivityCount,LiveStream,MethodCount");

CREATE TABLE analytics_live_stream (
    id INT(11) NOT NULL AUTO_INCREMENT,
    country VARCHAR(250) NULL DEFAULT  NULL,
    chatport INT(11) NULL DEFAULT  NULL,
    chatserverip VARCHAR(50) NULL DEFAULT  NULL,
    streamport INT(11) NULL DEFAULT  NULL,
    streamserverip VARCHAR(50) NULL DEFAULT  NULL,
    tags VARCHAR(250) NULL DEFAULT  NULL,
    transactiontime BIGINT(20) NULL DEFAULT  NULL,
    userstatus TINYINT(1) NULL DEFAULT  NULL,
    viewerserverip VARCHAR(50) NULL DEFAULT  NULL,
    viewerserverport INT(11) NULL DEFAULT  NULL,
    devicecategory INT(11) NULL DEFAULT  NULL,
    endtime BIGINT(20) NULL DEFAULT  NULL,
    gifton INT(11) NULL DEFAULT  NULL,
    isfeatured TINYINT(1) NULL DEFAULT  NULL,
    latitude DOUBLE NULL DEFAULT  NULL,
    likecount BIGINT(20) NULL DEFAULT  NULL,
    longitude DOUBLE NULL DEFAULT  NULL,
    name text NULL DEFAULT  NULL,
    profileimage text NULL DEFAULT  NULL,
    ringid BIGINT(20) NULL DEFAULT  NULL,
    starttime BIGINT(20) NULL DEFAULT  NULL,
    title text NULL DEFAULT  NULL,
    userid BIGINT(20) NULL DEFAULT  NULL,
    streamid VARCHAR(50) NULL DEFAULT  NULL,
    logtime BIGINT(20) NULL DEFAULT  NULL,
    slslogtime BIGINT(20) NULL DEFAULT  NULL, 
    viewcount BIGINT(20) NULL DEFAULT  NULL,
    startcoin int(11) default -2,
    endcoin int(11) default -2,
    PRIMARY KEY (id),
    UNIQUE KEY (streamid)
);

ALTER TABLE analytics_live_stream ADD userType INT(11);
ALTER TABLE analytics_live_stream ADD roomid BIGINT(20);
ALTER TABLE analytics_live_stream ADD device INT(11);
ALTER TABLE analytics_live_stream ADD tariff INT(11);
ALTER TABLE analytics_live_stream ADD featuredScore DOUBLE;
ALTER TABLE analytics_live_stream ADD streamMediaType INT(11);

ALTER TABLE analytics_live_stream DROP index streamid;
ALTER TABLE analytics_live_stream DROP slslogtime;


CREATE TABLE analytics_user_entry(
id bigint(20) auto_increment,
time bigint(20) NOT NULL,
userid bigint(20) NOT NULL,
PRIMARY KEY (id),
UNIQUE KEY (time, userid)
);


CREATE TABLE analytics_unique_user_count (
id int(11) NOT NULL AUTO_INCREMENT,
time bigint(20) DEFAULT NULL,
count bigint(20) DEFAULT NULL,
PRIMARY KEY (id),
UNIQUE KEY (time)
);

CREATE TABLE analytics_live_viewer_entry(
id bigint(20) auto_increment,
time bigint(20) NOT NULL,
viewerid bigint(20) NOT NULL,
PRIMARY KEY (id),
UNIQUE KEY (time, viewerid)
);


CREATE TABLE analytics_unique_live_viewer_count (
id bigint(20) auto_increment,
time bigint(20) DEFAULT NULL,
count bigint(20) DEFAULT NULL,
PRIMARY KEY (id),
UNIQUE KEY (time)
);

CREATE TABLE analytics_user_online_status (
time bigint(20),
userid bigint(20),
status int(11) DEFAULT NULL,
PRIMARY KEY (time, userid)
);
