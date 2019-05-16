import React from 'react';
import DynamicGroup from './DynamicGroup';
import DynamicInput from './DynamicInput';

export default (content) => {
    if (!content) {
        return <React.Fragment />;
    }

    return (
        <React.Fragment>
            {content.map(({ type, key, ...props }) => {
                const componentKey = `dynamic-layout-${key}`;
                let Tag;

                switch (type) {
                    case 'ROW':
                    case 'COL':
                        Tag = DynamicGroup;
                        break;
                    case 'INPUT':
                        Tag = DynamicInput;
                        break;
                    default:
                        return <React.Fragment key={componentKey} />;
                }

                return (
                    <Tag
                        key={`dynamic-layout-${componentKey}`}
                        type={type}
                        {...props}
                    />
                );
            })}
        </React.Fragment>
    );
};
