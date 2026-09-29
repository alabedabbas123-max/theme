Professional local dictionary assets

- ar_frequency_15k.txt and en_frequency_15k.txt: top filtered entries derived from hermitdave/FrequencyWords (OpenSubtitles 2018 frequency data). Content license: CC BY-SA 4.0. Source: https://github.com/hermitdave/FrequencyWords
- ar_bigrams.tsv and en_bigrams.tsv: curated starter phrase transitions for local next-word prediction.
- ar_trigrams.tsv and en_trigrams.tsv: curated three-word starter contexts; the database continuously expands them through local learning.
- ar_seed.txt and en_seed.txt: small emergency fallback dictionaries.

The user may import larger dictionaries from Settings. Format: word followed by optional integer frequency.
All learning remains in the local SQLite database.
