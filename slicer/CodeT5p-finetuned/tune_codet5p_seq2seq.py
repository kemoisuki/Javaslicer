"""
Finetune CodeT5+ models on any Seq2Seq LM tasks
You can customize your own training data by following the HF dataset format to cache it to args.cache_data
Author: Yue Wang
Date: June 2023
"""

import os
os.environ['HF_ENDPOINT'] = 'https://hf-mirror.com'
import pprint
import argparse
from datasets import load_dataset, load_from_disk, Dataset
from transformers import AutoModelForSeq2SeqLM, AutoTokenizer, TrainingArguments, Trainer
import re
from itertools import groupby

def run_training(args, model, train_data):
    print(f"Starting main loop")

    training_args = TrainingArguments(
        report_to='tensorboard',
        output_dir=args.save_dir,
        overwrite_output_dir=False,

        do_train=True,
        save_strategy='epoch',

        num_train_epochs=args.epochs,
        per_device_train_batch_size=args.batch_size_per_replica,
        gradient_accumulation_steps=args.grad_acc_steps,

        learning_rate=args.lr,
        weight_decay=0.05,
        warmup_steps=args.lr_warmup_steps,

        logging_dir=args.save_dir,
        logging_first_step=True,
        logging_steps=args.log_freq,
        save_total_limit=1,

        dataloader_drop_last=True,
        dataloader_num_workers=4,

        local_rank=args.local_rank,
        deepspeed=args.deepspeed,
        fp16=args.fp16,
    )

    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_data,
    )

    trainer.train()

    if args.local_rank in [0, -1]:
        final_checkpoint_dir = os.path.join(args.save_dir, "final_checkpoint")
        model.save_pretrained(final_checkpoint_dir)
        print(f'  ==> Finish training and save to {final_checkpoint_dir}')


def load_tokenize_data(args):
    # Load and tokenize data
    if os.path.exists(args.cache_data):
        train_data = load_from_disk(args.cache_data)
        print(f'  ==> Loaded {len(train_data)} samples')
        return train_data
    else:
        # Load your txt file
        with open(args.data_file, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        data = []
        for is_empty, group in groupby(lines, key=lambda line: line.strip() == ''):
            if not is_empty:
                group = list(group)
                code = ''.join(group[:-1]).strip()
                comment = group[-1].strip()
                data.append({'input': code, 'target': comment})

        # Tokenize data
        tokenizer = AutoTokenizer.from_pretrained(args.load)

        def preprocess_function(examples):
            source = examples['input']
            target = examples['target']

            model_inputs = tokenizer(source, max_length=args.max_source_len, padding="max_length", truncation=True)
            labels = tokenizer(target, max_length=args.max_target_len, padding="max_length", truncation=True)

            model_inputs["labels"] = labels["input_ids"].copy()
            model_inputs["labels"] = [
                [(l if l != tokenizer.pad_token_id else -100) for l in label] for label in model_inputs["labels"]
            ]
            return model_inputs

        data_dict = {'input': [d['input'] for d in data], 'target': [d['target'] for d in data]}
        train_data = Dataset.from_dict(data_dict).map(
            preprocess_function,
            batched=True,
            remove_columns=['input', 'target'],
            num_proc=32,
            load_from_cache_file=False,
        )
        print(f'  ==> Loaded {len(train_data)} samples')
        train_data.save_to_disk(args.cache_data)
        print(f'  ==> Saved to {args.cache_data}')
        return train_data

def main(args):
    argsdict = vars(args)
    print(pprint.pformat(argsdict))

    # Save command to file
    with open(os.path.join(args.save_dir, "command.txt"), 'w') as f:
        f.write(pprint.pformat(argsdict))

    # Load and tokenize data using the tokenizer from `args.load`. If the data is already cached, load it from there.
    # You can customize this function to load your own data for any Seq2Seq LM tasks.
    train_data = load_tokenize_data(args)

    if args.data_num != -1:
        train_data = train_data.select([i for i in range(args.data_num)])

    # Load model from `args.load`
    model = AutoModelForSeq2SeqLM.from_pretrained(args.load)
    print(f"  ==> Loaded model from {args.load}, model size {model.num_parameters()}")

    run_training(args, model, train_data)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="CodeT5+ finetuning on Seq2Seq LM task")
    parser.add_argument('--data-file', default='path/to/your/data.txt', type=str)
    parser.add_argument('--data-num', default=-1, type=int)
    parser.add_argument('--max-source-len', default=256, type=int)
    parser.add_argument('--max-target-len', default=128, type=int)
    parser.add_argument('--cache-data', default='cache_data/summarize_python', type=str)
    parser.add_argument('--load', default='Salesforce/codet5p-220m', type=str)

    # Trainin
    parser.add_argument('--epochs', default=15, type=int)

    parser.add_argument('--lr', default=5e-5, type=float)
    parser.add_argument('--lr-warmup-steps', default=200, type=int)
    parser.add_argument('--batch-size-per-replica', default=16, type=int)
    parser.add_argument('--grad-acc-steps', default=4, type=int)
    parser.add_argument('--local_rank', default=-1, type=int)
    parser.add_argument('--deepspeed', default=None, type=str)
    parser.add_argument('--fp16', default=False, action='store_true')

    # Logging and stuff
    parser.add_argument('--save-dir', default="saved_models/summarize_python", type=str)
    parser.add_argument('--log-freq', default=10, type=int)
    parser.add_argument('--save-freq', default=500, type=int)

    args = parser.parse_args()

    os.makedirs(args.save_dir, exist_ok=True)

    main(args)
