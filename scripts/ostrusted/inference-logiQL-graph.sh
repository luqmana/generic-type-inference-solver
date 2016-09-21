#!/bin/bash

export MYDIR=`dirname $0`/..
. $MYDIR/setup.sh
export ROOT=$MYDIR/../..
export LOGICDATA=./logiqldata
distDir=$CHINF"/dist"
java -classpath "$distDir"/checker.jar:"$distDir"/plume.jar:"$distDir"/checker-framework-inference.jar:$ROOT/generic-type-inference-solver/bin checkers.inference.InferenceLauncher --solverArgs="backEndType=logiqlbackend.LogiQL" --checker ostrusted.OsTrustedChecker --solver constraintsolver.ConstraintSolver --mode INFER --hacks=true $*
rm -r $LOGICDATA