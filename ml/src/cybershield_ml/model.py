"""Pure-Python logistic regression baseline + temperature calibration.

This is the Phase-0 stand-in that makes the whole pipeline runnable with no dependencies. The
production model (distilled MuRIL -> LiteRT) replaces this class at the train/export step; everything
around it (data, features, evaluation, gate, model card) stays the same.
"""
from __future__ import annotations

import math
import random


def _sigmoid(z: float) -> float:
    if z >= 0:
        return 1.0 / (1.0 + math.exp(-z))
    e = math.exp(z)
    return e / (1.0 + e)


class LogisticRegression:
    def __init__(self, dim: int):
        self.dim = dim
        self.w = [0.0] * dim
        self.b = 0.0
        self.temperature = 1.0  # calibration; 1.0 = uncalibrated

    def _logit(self, feats: dict[int, float]) -> float:
        z = self.b
        for idx, val in feats.items():
            z += self.w[idx] * val
        return z

    def predict_logit(self, feats: dict[int, float]) -> float:
        return self._logit(feats) / self.temperature

    def predict_proba(self, feats: dict[int, float]) -> float:
        return _sigmoid(self.predict_logit(feats))

    def fit(self, X: list[dict[int, float]], y: list[int], *, epochs: int, lr: float, l2: float, seed: int):
        rng = random.Random(seed)
        n = len(X)
        order = list(range(n))
        for _ in range(epochs):
            rng.shuffle(order)
            for i in order:
                feats = X[i]
                p = _sigmoid(self._logit(feats))
                err = p - y[i]  # dLoss/dz for log-loss
                self.b -= lr * err
                for idx, val in feats.items():
                    grad = err * val + l2 * self.w[idx]
                    self.w[idx] -= lr * grad
        return self

    def calibrate_temperature(self, X: list[dict[int, float]], y: list[int]):
        """Temperature scaling: fit a single scalar T on held-out data by minimising NLL."""
        if not X:
            self.temperature = 1.0
            return self.temperature
        logits = [self._logit(f) for f in X]
        T = 1.0
        lr = 0.05
        for _ in range(300):
            grad = 0.0
            for z, target in zip(logits, y):
                p = _sigmoid(z / T)
                # d NLL / d T  (chain rule through z/T)
                grad += (p - target) * (-z / (T * T))
            grad /= len(logits)
            T -= lr * grad
            if T < 0.05:
                T = 0.05
        self.temperature = T
        return T

    def to_dict(self) -> dict:
        # Store only non-zero weights to keep the file small.
        nz = {str(i): wv for i, wv in enumerate(self.w) if wv != 0.0}
        return {"dim": self.dim, "bias": self.b, "temperature": self.temperature, "weights": nz}

    @classmethod
    def from_dict(cls, d: dict) -> "LogisticRegression":
        m = cls(int(d["dim"]))
        m.b = float(d["bias"])
        m.temperature = float(d.get("temperature", 1.0))
        for i, wv in d["weights"].items():
            m.w[int(i)] = float(wv)
        return m
