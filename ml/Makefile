# CyberShield ML — one-command pipeline. Pure Python stdlib; no pip install required.
PY ?= python3
export PYTHONPATH := src
CLI = $(PY) -m cybershield_ml.cli

.PHONY: all snapshot train eval gate test clean

all:       ## snapshot -> train -> calibrate -> evaluate -> gate (report mode)
	$(CLI) all

snapshot:  ## build a hash-pinned data snapshot from the Android eval TSVs
	$(CLI) snapshot

train:     ## train the baseline model and write models/<version>/
	$(CLI) train

eval:      ## evaluate on dev + holdout and write the metrics card
	$(CLI) eval

gate:      ## compare holdout metrics to the rule baseline (report/enforce per config.yaml)
	$(CLI) gate

test:      ## fast pipeline self-tests
	$(PY) -m cybershield_ml.tests.test_pipeline

clean:     ## remove generated models and snapshots
	rm -rf models/*/ data/snapshots/
