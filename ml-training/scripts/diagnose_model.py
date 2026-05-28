"""Diagnose xem model TFLite co thuc su trained hay khong."""
from pathlib import Path
import numpy as np

try:
    from tflite_runtime.interpreter import Interpreter
except ImportError:
    from tensorflow.lite.python.interpreter import Interpreter

ROOT = Path(__file__).resolve().parents[2]
MODEL_PATH = ROOT / "app" / "src" / "main" / "assets" / "ml" / "vnd_classifier.tflite"
LABELS = ['500000', '200000', '100000', '50000', '20000', '10000', '5000', '2000', '1000', 'unknown']

interpreter = Interpreter(model_path=str(MODEL_PATH))
interpreter.allocate_tensors()
inp = interpreter.get_input_details()[0]
out = interpreter.get_output_details()[0]


def softmax(x):
    x = x - x.max()
    e = np.exp(x)
    return e / e.sum()


print(f"Model: {MODEL_PATH}")
print(f"Input  shape={inp['shape']} dtype={inp['dtype'].__name__}")
print(f"Output shape={out['shape']} dtype={out['dtype'].__name__}\n")

# Generate 5 hoan toan khac nhau test inputs (random noise voi seed khac nhau)
print("=" * 70)
print("Test 5 random inputs - neu logits gan giong nhau = model BROKEN")
print("=" * 70)

for seed in range(5):
    rng = np.random.RandomState(seed)
    x = rng.rand(1, 224, 224, 3).astype(np.float32) * 255  # gia uint8 range
    # Normalize ImageNet
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32) * 255
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32) * 255
    x = (x - mean) / std

    interpreter.set_tensor(inp['index'], x)
    interpreter.invoke()
    logits = interpreter.get_tensor(out['index'])[0]
    probs = softmax(logits)

    print(f"\nSeed {seed}:")
    print(f"  Logits range: min={logits.min():+.3f}  max={logits.max():+.3f}  spread={logits.max()-logits.min():.3f}")
    top3 = np.argsort(probs)[::-1][:3]
    for i in top3:
        print(f"    {LABELS[i]:>10}: logit={logits[i]:+.3f}  prob={probs[i]*100:5.1f}%")

print("\n" + "=" * 70)
print("CHAN DOAN:")
print("=" * 70)
print("Neu logits spread < 0.5 (gan deu) tren MOI seed = model RANDOM WEIGHTS")
print("  -> can train lai + export DUNG checkpoint da train")
print("Neu logits spread > 3 + top-1 dao loan giua cac seed = model TRAINED OK")
print("  -> model hoc duoc nhung khong generalize tu Kaggle -> webcam")
print("Neu logits spread > 3 nhung TOP-1 LUON LA UNKNOWN = unknown class overfit")
print("  -> bot CIFAR samples hoac thay bang anh non-money real-world")
