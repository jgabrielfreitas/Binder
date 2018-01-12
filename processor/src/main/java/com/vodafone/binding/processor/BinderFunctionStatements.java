package com.vodafone.binding.processor;

import com.chaining.Chain;
import com.functional.curry.Curry;
import com.vodafone.binding.annotations.SubscribeTo;
import com.vodafone.binding.annotations.SubscriptionName;

import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Function;

import static com.vodafone.binding.processor.BinderCodeGenerator.VARIABLE_NAME_DISPOSABLES;

class BinderFunctionStatements implements Function<BoundTypes, List<String>> {

    private final ProcessingEnvironment environment;

    BinderFunctionStatements(ProcessingEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public List<String> apply(BoundTypes boundTypes) {
        return Chain.let(new ArrayList<String>())
                .apply(lines -> lines.add(declareCompositeDisposable()))
                .apply(lines -> lines.add(initializeCompositeDisposable()))
                .apply(lines -> lines.addAll(addMethodsToCompositeDisposable(boundTypes)))
                .apply(lines -> lines.add(returnCompositeDisposables()))
                .call();
    }

    private String declareCompositeDisposable() {
        return CompositeDisposable.class.getName() + " " + VARIABLE_NAME_DISPOSABLES;
    }

    private String initializeCompositeDisposable() {
        return VARIABLE_NAME_DISPOSABLES + " = new " + CompositeDisposable.class.getName() + "()";
    }

    private List<String> addMethodsToCompositeDisposable(BoundTypes boundTypes) {
        return Observable.just(subscribersMap(boundTypes))
                .map(Map::entrySet)
                .flatMap(Observable::fromIterable)
                .map(Curry.toFunction(this::toBoundElements, sourcesMap(boundTypes)))
                .flatMap(Observable::fromIterable)
                .map(new BoundElementsMerger())
                .toList()
                .blockingGet();
    }

    private String returnCompositeDisposables() {
        return "return " + VARIABLE_NAME_DISPOSABLES;
    }

    private Map<String, ? extends Collection<? extends Element>> subscribersMap(BoundTypes boundTypes) {
        return Observable.just(boundTypes)
                .map(BoundTypes::getElementWithSubscribeToAnnotations)
                .map(TypeElement::getEnclosedElements)
                .flatMap(Observable::fromIterable)
                .filter(element -> element.getAnnotation(SubscribeTo.class) != null)
                .toMultimap(element -> element.getAnnotation(SubscribeTo.class).value())
                .blockingGet();
    }

    private Map<String, ? extends Element> sourcesMap(BoundTypes boundTypes) {
        return Chain.let(sourceObservables(boundTypes))
                .apply(observable -> showErrorOnDuplicateAnnotationValues(boundTypes, observable))
                .call()
                .toMap(this::bySubscriptionNameValue)
                .blockingGet();
    }

    private Observable<? extends Element> sourceObservables(BoundTypes boundTypes) {
        return Observable.just(boundTypes)
                .map(BoundTypes::getElementWithSubscriptionNameAnnotations)
                .map(TypeElement::getEnclosedElements)
                .flatMap(Observable::fromIterable)
                .filter(element -> element.getAnnotation(SubscriptionName.class) != null);
    }

    private void showErrorOnDuplicateAnnotationValues(BoundTypes boundTypes,
                                                      Observable<? extends Element> sourcesObservable) {
        Chain.let(sourcesObservable)
                .map(observable -> observable.map(this::bySubscriptionNameValue))
                .pair(observable -> observable.toList().blockingGet())
                .whenNot(pair -> pair.getValue1().size() == uniqueAnnotationValues(pair.getValue0()))
                .thenMap(Pair::getValue1)
                .apply(Curry.toConsumer(this::showCompilationError, boundTypes));
    }

    private int uniqueAnnotationValues(Observable<String> annotationValues) {
        return annotationValues.distinct().toList().blockingGet().size();
    }

    private void showCompilationError(BoundTypes boundTypes, List<String> annotatedMembers) {
        printError("values of @" + SubscriptionName.class.getSimpleName() + " should be unique");
        printError("found in "
                + boundTypes.getElementWithSubscriptionNameAnnotations()
                + " the following  values for @"
                + SubscriptionName.class.getSimpleName()
                + " : " + annotatedMembers);
    }

    private void printError(Object object) {
        environment.getMessager().printMessage(Diagnostic.Kind.ERROR, String.valueOf(object));
    }

    private List<BoundElements> toBoundElements(Map<String, ? extends Element> sources,
                                                Map.Entry<String, ? extends Collection<? extends Element>> entry) {
        return Observable.fromIterable(entry.getValue())
                .map(Curry.toFunction(BoundElements::new, sources.get(entry.getKey())))
                .toList()
                .blockingGet();
    }

    private String bySubscriptionNameValue(Element element) {
        return element.getAnnotation(SubscriptionName.class).value();
    }
}