-- Tuketici uygulamanin KENDI Flyway migration'ini temsil eder (kutuphaneninkinden
-- tamamen bagimsiz). NotificationConsumerFlywayCoexistenceTest bunun gercekten
-- calistigini dogrular - yani kutuphane Boot'un Flyway'ini "ele gecirmemistir".
CREATE TABLE IF NOT EXISTS consumer_widget (
    id INTEGER PRIMARY KEY
);
