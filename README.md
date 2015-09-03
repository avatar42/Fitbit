# Fitbit
A quick and dirty program for getting your data down loaded before the Aug 27th shutdown of custom trackers and journals

Update Fitbit has extended the deadline to Monday Aug 31th.
I've stuck a jar file at https://drive.google.com/open?id=0B0zp3DlgiRDQNXlNX1VnMG9YZFE for a those non programmers still struggling to get their data off Fitbit before Monday. Once Fitbit removes journals I'll be deleting the jar

To run the program

edit the line below replacing:
YOUR_LOGIN with your login (email)
YOUR_PASSOWRD with your fitbit password
DATE_OF_FIRST_JOURNAL with date of oldest journal entry.
as in java -jar target/FitbitReader.jar bob@gmail.com P@$$w0rd 8/31/2013

java -jar target/FitbitReader.jar YOUR_LOGIN YOUR_PASSOWRD DATE_OF_FIRST_JOURNAL

Update 9/2/2015 Fitbit removed the tab but the URL is still there so you can still get your data if you hurry.