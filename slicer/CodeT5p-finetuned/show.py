import os
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM,AutoModel
import sacrebleu
from tqdm import tqdm
from itertools import islice
from itertools import groupby
import torch

# 加载 tokenizer
tokenizer = AutoTokenizer.from_pretrained("Salesforce/codet5p-220m-bimodal",trust_remote_code=True)

# 加载微调后的模型
model = AutoModelForSeq2SeqLM.from_pretrained("/root/autodl-tmp/project/CodeT5main/CodeT5+/saved_models/summarize_python/final_checkpoint/model.safetensors", trust_remote_code=True)
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model.to(device)

# 加载测试数据txt文件
with open("/root/autodl-tmp/project/Test.txt", 'r', encoding='utf-8') as f:
    lines = f.readlines()

data = []
for is_empty, group in groupby(lines, key=lambda line: line.strip() == ''):
    if not is_empty:
        group = list(group)
        code = ''.join(group[:-1]).strip()
        comment = group[-1].strip()
        data.append({'input': code, 'target': comment})
print(f"测试集中共有 {len(data)} 对数据")

preds = []
targets = []
batch_size = 48
# 遍历测试数据集
# for item in tqdm(data):
#     input_ids = tokenizer(item['input'], return_tensors="pt", truncation=True, max_length=512).input_ids.to(device)
#     output_ids = model.generate(input_ids, max_length=100, early_stopping=True)
#     pred = tokenizer.decode(output_ids[0], skip_special_tokens=True)
#     preds.append(pred)
#     targets.append(item['target'])
# 遍历测试数据集
for i in tqdm(range(0, len(data), batch_size)):
    batch = data[i:i+batch_size]
    input_ids = tokenizer([item['input'] for item in batch], return_tensors="pt", truncation=True, max_length=512, padding=True).input_ids.to(device)
    output_ids = model.generate(input_ids, max_length=100, early_stopping=True)
    preds.extend(tokenizer.batch_decode(output_ids, skip_special_tokens=True))
    targets.extend([item['target'] for item in batch])

with open('/root/autodl-tmp/project/preds/codeT5+preds.txt', 'w', encoding='utf-8') as f:
    for pred in preds:
        f.write(pred + '\n')

# 计算 BLEU-4 分数
bleu = sacrebleu.corpus_bleu(preds, [targets])
print(f"BLEU-4 Score: {bleu.score}")