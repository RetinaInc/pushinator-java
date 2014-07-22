#!/bin/sh

mvn clean package appassembler:assemble
test -d dist && rm -rf dist
mv target/appassembler dist