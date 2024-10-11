#!/bin/sh

exitCodeCheck(){
if [ $1 -ne 0 ]; then
  echo 'shell execute return value is' $1 'is not 0'
  exit $1
else
  echo 'shell execute success'
fi
}
