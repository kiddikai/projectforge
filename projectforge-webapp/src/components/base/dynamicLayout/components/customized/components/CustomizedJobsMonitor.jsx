import PropTypes from 'prop-types';
import React, { useContext, useEffect, useMemo, useRef } from 'react';
import { DynamicLayoutContext } from '../../../context';
import { fetchJsonGet } from '../../../../../../utilities/rest';
import DynamicProgress from '../../DynamicProgress';

function CustomizedJobsMonitor(props) {
    const { values } = props;
    const {
        variables, setVariables, callAction,
    } = useContext(DynamicLayoutContext);

    const {
        jobId, all, fetchUpdateInterval, cancelConfirmMessage,
    } = values;

    const interval = useRef();
    const fetchUpdateIntervalRef = useRef(fetchUpdateInterval);
    const jobIdRef = useRef(jobId);
    const allRef = useRef(all);
    const cancelConfirmMessageRef = useRef(cancelConfirmMessage);

    const jobs = Object.getByString(variables, 'jobs');

    useEffect(() => {
        jobIdRef.current = jobId;
        allRef.current = all;
        fetchUpdateIntervalRef.current = fetchUpdateInterval;
        cancelConfirmMessageRef.current = cancelConfirmMessage;
    }, [jobId, all, fetchUpdateInterval, cancelConfirmMessage]);

    const fetchJobsList = () => {
        fetchJsonGet(
            'jobsMonitor/jobs',
            { jobId: jobIdRef.current, all: allRef.current },
            (json) => callAction({ responseAction: json }),
        );
    };

    const onCancelClick = (cancelJobId) => {
        fetchJsonGet(
            'jobsMonitor/cancel',
            { jobId: cancelJobId },
            (json) => callAction({ responseAction: json }),
        );
    };

    useEffect(() => {
        interval.current = setInterval(
            () => fetchJobsList(),
            fetchUpdateIntervalRef.current || 1000,
        );
        return () => {
            clearInterval(interval.current);
        };
    }, []);

    useEffect(() => {
        fetchUpdateIntervalRef.current = fetchUpdateInterval;
    }, [fetchUpdateInterval]);
    return useMemo(
        () => (
            <div>
                {jobs && jobs.map((job) => (
                    <DynamicProgress
                        value={job.progressPercentage}
                        color={job.progressBarColor}
                        animated={job.animated}
                        title={job.progressTitle}
                        onCancelClick={onCancelClick}
                        cancelConfirmMessage={cancelConfirmMessageRef.current}
                        cancelId={job.cancelId}
                    />
                ))}
            </div>
        ),
        [variables, setVariables],
    );
}

CustomizedJobsMonitor.propTypes = {
    jobId: PropTypes.string,
    all: PropTypes.bool,
    fetchUpdateInterval: PropTypes.number,
};

CustomizedJobsMonitor.defaultProps = {
    jobId: undefined,
    all: undefined,
    fetchUpdateInterval: undefined,
};

export default CustomizedJobsMonitor;
