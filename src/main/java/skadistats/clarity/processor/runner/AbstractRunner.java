package skadistats.clarity.processor.runner;

import skadistats.clarity.LogChannel;
import skadistats.clarity.event.Event;
import skadistats.clarity.event.InsertEvent;
import skadistats.clarity.event.Provides;
import skadistats.clarity.logger.Logger;
import skadistats.clarity.logger.Logging;
import skadistats.clarity.model.EngineType;

@Provides({OnInit.class})
public abstract class AbstractRunner implements Runner {

    protected static final Logger log = Logging.getLogger(LogChannel.runner);

    @InsertEvent
    private Event<OnInit> evInitRun;

    protected final EngineType engineType;
    protected Context context;

    public AbstractRunner(EngineType engineType) {
        this.engineType = engineType;
    }

    private ExecutionModel createExecutionModel(Object... processors) {
        ExecutionModel executionModel = new ExecutionModel(this);
        for (Object p : processors) {
            executionModel.addProcessor(p);
        }
        return executionModel;
    }

    protected void initWithProcessors(Object... processors) {
        ExecutionModel em = createExecutionModel(processors);
        context = new Context(em);
        em.initialize(context);
        if (evInitRun != null) {
            evInitRun.raise();
        }
    }

    @Override
    public EngineType getEngineType() {
        return engineType;
    }

    @Override
    public Context getContext() {
        return context;
    }

}
