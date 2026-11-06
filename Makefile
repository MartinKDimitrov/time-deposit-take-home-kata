# Single entry point for building and testing. Git hooks, CLAUDE.md and the AI agents use these targets.
.DEFAULT_GOAL := help
.PHONY: help setup test verify run

help:   ## List the available targets
	@grep -E '^[a-z]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  make %-7s %s\n", $$1, $$2}'

setup:  ## Enable the git hooks and check Java and Docker
	git config core.hooksPath .githooks
	@java -version 2>&1 | head -1
	@docker info >/dev/null 2>&1 && echo "Docker: running" || echo "Docker: NOT running (needed by make verify / make run)"

test:   ## Compile and run unit tests, no Docker (one class: make test T=TimeDepositCalculatorTest)
	./mvnw -q test $(if $(T),-Dtest=$(T))

verify: ## Unit + integration tests with Testcontainers (Docker required)
	./mvnw -q verify

run:    ## Start the app with demo data in a PostgreSQL container: http://localhost:8080/swagger (Docker required)
	./mvnw -q test-compile exec:java -Dexec.mainClass=org.ikigaidigital.LocalApplicationKt -Dexec.classpathScope=test
