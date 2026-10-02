JAR_NAME := app.jar
DIST_DIR := dist

.PHONY: build dist run clean test

build:
	./mvnw clean package -DskipTests

dist: build
	mkdir -p $(DIST_DIR)
	cp $$(find target -maxdepth 1 -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*.original' | head -1) $(DIST_DIR)/$(JAR_NAME)

run: dist
	java -jar $(DIST_DIR)/$(JAR_NAME)

test:
	./mvnw test

clean:
	./mvnw clean
	rm -rf $(DIST_DIR)
