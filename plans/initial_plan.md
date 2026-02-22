# High-Performance Rule Engine Specification

## 1. Input & Output
* **Source:** A list of URL strings provided via a batch file.
* **Processing:** Decompose each URL into its constituent parts (e.g., Host, Path, Query).
* **Result:** For each URL, return the associated result string based on rule matching.

## 2. Supported Operators & Logic
The engine must support the following operators for any URL part, including **negation** support for each:
* **Equals:** Exact string match.
* **Contains:** Substring existence.
* **Start with:** Prefix match.
* **End with:** Suffix match.

## 3. Rule Requirements
* **Multi-Operator Support:** A single rule can combine multiple conditions across different URL parts.
* **Example Case:** A rule where `Host` ends with `.ca` AND `Path` contains `sport` must return "Canada Sport".

## 4. Data Structure Selection
The implementation must select and justify the most efficient data structure for each specific operator to ensure high-performance batch processing:
* **Equals:** Structure optimized for $O(1)$ lookups.
* **Contains:** Structure optimized for multi-pattern substring matching.
* **Start with:** Structure optimized for prefix-based traversal.
* **End with:** Structure optimized for suffix-based traversal.



## 5. Testing & Validation
**Unit tests must be created for every component of the system, including:**
* **Isolated Operator Tests:** Validation of `equals`, `contains`, `start with`, and `end with`.
* **Negated Logic Tests:** Ensuring the negation flag correctly inverts results for all operators.
* **Compound Rule Tests:** Validating rules with multiple operators (e.g., the "Canada Sport" example).
* **Batch Processing Tests:** Verifying the ingestion and processing of the input list from the file.
* **Edge Case Tests:** Handling malformed URLs, empty strings, and case-sensitivity.
