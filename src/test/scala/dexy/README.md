# Testing template

For each box in the input of a transaction test the following:

1. Cannot change address of corresponding output box
2. Cannot change tokens of corresponding output box (except when allowed)
3. Cannot decrease ergs value (except when allowed)
4. Cannot add junk tokens
5. Cannot add junk registers
6. Cannot accept other boxes (inputs, data inputs) with wrong NFTs
7. App specific tests, for example "LP tokens should not reduce during intervention"
