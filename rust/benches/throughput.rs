use criterion::{criterion_group, criterion_main, Criterion, Throughput};
use rayon::prelude::*;
use rule_engine::engine::RuleEngine;
use rule_engine::url::{ParsedUrl, UrlParser};

mod data_generator;
use data_generator::DataGenerator;

// ---------------------------------------------------------------------------
// helpers
// ---------------------------------------------------------------------------

/// Single-threaded: evaluate every URL sequentially on the calling thread.
fn evaluate_single_thread(engine: &RuleEngine, urls: &[ParsedUrl]) -> u64 {
    let mut count = 0u64;
    for url in urls {
        if engine.evaluate(url).is_some() {
            count += 1;
        }
    }
    count
}

/// Multi-threaded: evaluate URLs in parallel using a rayon pool of `threads`.
fn evaluate_multi_thread(engine: &RuleEngine, urls: &[ParsedUrl], threads: usize) -> u64 {
    let pool = rayon::ThreadPoolBuilder::new()
        .num_threads(threads)
        .build()
        .unwrap();
    pool.install(|| {
        urls.par_iter()
            .map(|url| if engine.evaluate(url).is_some() { 1u64 } else { 0 })
            .sum()
    })
}

// ---------------------------------------------------------------------------
// standard benchmarks (~2K rules, ~200K URLs)
// ---------------------------------------------------------------------------

fn standard_benchmark(c: &mut Criterion) {
    let mut datagen = DataGenerator::new(42);
    let rules = datagen.generate_rules();
    let urls = datagen.generate_urls();

    let parsed: Vec<_> = urls
        .iter()
        .filter_map(|u| UrlParser::parse(u).ok())
        .collect();

    let engine = RuleEngine::new(rules);
    let n_urls = parsed.len() as u64;

    eprintln!("Standard benchmark: {} URLs ({} parsed)", urls.len(), n_urls);

    let mut group = c.benchmark_group("standard");
    group.throughput(Throughput::Elements(n_urls));
    group.sample_size(10);

    group.bench_function("1_thread", |b| {
        b.iter(|| evaluate_single_thread(&engine, &parsed));
    });

    group.bench_function("10_threads", |b| {
        b.iter(|| evaluate_multi_thread(&engine, &parsed, 10));
    });

    group.finish();
}

// ---------------------------------------------------------------------------
// large benchmarks (~100K rules, ~200K URLs)
// ---------------------------------------------------------------------------

fn large_benchmark(c: &mut Criterion) {
    let mut datagen = DataGenerator::new(42);
    let rules = datagen.generate_large_rule_set();
    let urls = datagen.generate_large_url_set();

    let parsed: Vec<_> = urls
        .iter()
        .filter_map(|u| UrlParser::parse(u).ok())
        .collect();

    let engine = RuleEngine::new(rules);
    let n_urls = parsed.len() as u64;

    eprintln!("Large benchmark: {} parsed URLs", n_urls);

    let mut group = c.benchmark_group("large");
    group.throughput(Throughput::Elements(n_urls));
    group.sample_size(10);

    group.bench_function("1_thread", |b| {
        b.iter(|| evaluate_single_thread(&engine, &parsed));
    });

    group.bench_function("10_threads", |b| {
        b.iter(|| evaluate_multi_thread(&engine, &parsed, 10));
    });

    group.finish();
}

// ---------------------------------------------------------------------------
// harness
// ---------------------------------------------------------------------------

criterion_group!(benches, standard_benchmark);
criterion_group!(large_benches, large_benchmark);
criterion_main!(benches, large_benches);
