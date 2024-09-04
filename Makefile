MVN = mvn
MVN_FLAGS = -B

# Define targets and dependencies
.PHONY: clean compile run-client run-server build

# Build target
build:
	$(MVN) $(MVN_FLAGS) clean install

# Compile target
compile:
	$(MVN) $(MVN_FLAGS) compile

# Run client target
# configured in pom.xml
run-client:
	$(MVN) $(MVN_FLAGS) javafx:run -Pclient

# Run server target
# id 'server' in pom.xml
run-server:
	$(MVN) $(MVN_FLAGS) exec:java -Pserver

# Clean target
clean:
	$(MVN) $(MVN_FLAGS) clean
