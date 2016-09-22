#!/bin/bash

export MYDIR=`dirname $0`/..
. $MYDIR/setup.sh
export ROOT=$MYDIR/../..
distDir=$CHINF"/dist"
java -classpath "$distDir"/checker.jar:"$distDir"/plume.jar:"$distDir"/checker-framework-inference.jar:$ROOT/universe/bin  checkers.inference.InferenceLauncher --checker dataflow.DataflowChecker --solver checkers.inference.solver.DebugSolver --mode INFER $*