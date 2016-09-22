package dataflow.solver;

import org.checkerframework.framework.type.QualifierHierarchy;
import org.checkerframework.javacutil.AnnotationUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.util.Elements;

import checkers.inference.InferenceSolution;
import checkers.inference.InferenceSolver;
import checkers.inference.model.ConstantSlot;
import checkers.inference.model.Constraint;
import checkers.inference.model.Slot;
import constraintgraph.ConstraintGraph;
import constraintgraph.GraphBuilder;
import constraintgraph.Vertex;
import dataflow.qual.DataFlow;
import dataflow.util.DataflowUtils;

public class DataflowSolver implements InferenceSolver {

    protected AnnotationMirror DATAFLOW;

    @Override
    public InferenceSolution solve(Map<String, String> configuration,
            Collection<Slot> slots, Collection<Constraint> constraints,
            QualifierHierarchy qualHierarchy,
            ProcessingEnvironment processingEnvironment) {

        Elements elements = processingEnvironment.getElementUtils();
        DATAFLOW = AnnotationUtils.fromClass(elements, DataFlow.class);
        GraphBuilder graphBuilder = new GraphBuilder(slots, constraints);
        ConstraintGraph constraintGraph = graphBuilder.buildGraph();

        Collection<String> datatypesUsed = getDatatypesUsed(slots);
        List<DatatypeSolver> dataflowSolvers = new ArrayList<>();

        // Configure datatype solvers
        for (Map.Entry<Vertex, Set<Constraint>> entry : constraintGraph.getIndependentPath().entrySet()) {
            AnnotationMirror anno = entry.getKey().getValue();
            if (AnnotationUtils.areSameIgnoringValues(anno, DATAFLOW)) {
                String[] dataflowValues = DataflowUtils.getTypeNames(anno);
                String[] dataflowRoots = DataflowUtils.getTypeNameRoots(anno);
                if (dataflowValues.length == 1) {
                    DatatypeSolver solver = new DatatypeSolver(dataflowValues[0], entry.getValue(),getSerializer(dataflowValues[0], false));
                    dataflowSolvers.add(solver);
                } else if (dataflowRoots.length == 1) {
                    DatatypeSolver solver = new DatatypeSolver(dataflowRoots[0], entry.getValue(),getSerializer(dataflowRoots[0], true));
                    dataflowSolvers.add(solver);
                }
            }
        }

        // List<DatatypeSolution> solutions = new ArrayList<>();
        // for (DatatypeSolver solver : dataflowSolvers) {
        // solutions.add(solver.solve());
        // }
        List<DatatypeSolution> solutions = new ArrayList<>();
        try {
            if (dataflowSolvers.size() > 0) {
                solutions = solveInparallel(dataflowSolvers);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return getMergedSolution(processingEnvironment, solutions);
    }

    private List<DatatypeSolution> solveInparallel(List<DatatypeSolver> dataflowSolvers)
            throws InterruptedException, ExecutionException {
        ExecutorService service = Executors.newFixedThreadPool(dataflowSolvers.size());

        List<Future<DatatypeSolution>> futures = new ArrayList<Future<DatatypeSolution>>();

        for (final DatatypeSolver solver : dataflowSolvers) {
            Callable<DatatypeSolution> callable = new Callable<DatatypeSolution>() {
                @Override
                public DatatypeSolution call() throws Exception {
                    return solver.solve();
                }
            };
            futures.add(service.submit(callable));
        }
        service.shutdown();

        List<DatatypeSolution> solutions = new ArrayList<>();
        for (Future<DatatypeSolution> future : futures) {
            solutions.add(future.get());
        }
        return solutions;
    }

    private Collection<String> getDatatypesUsed(Collection<Slot> slots) {
        Set<String> types = new TreeSet<>();
        for (Slot slot : slots) {
            if (slot instanceof ConstantSlot) {
                ConstantSlot constantSlot = (ConstantSlot) slot;
                AnnotationMirror anno = constantSlot.getValue();
                if (AnnotationUtils.areSameIgnoringValues(anno, DATAFLOW)) {
                    String[] dataflowValues = DataflowUtils.getTypeNames(anno);
                    for (String dataflowValue : dataflowValues) {
                        types.add(dataflowValue);
                    }
                }
            }
        }
        return types;
    }

    protected DataflowSerializer getSerializer(String datatype, boolean isRoot) {
        return new DataflowSerializer(datatype, isRoot);
    }

    protected InferenceSolution getMergedSolution(ProcessingEnvironment processingEnvironment,
            List<DatatypeSolution> solutions) {
        return new DataflowSolution(solutions, processingEnvironment);
    }
}
