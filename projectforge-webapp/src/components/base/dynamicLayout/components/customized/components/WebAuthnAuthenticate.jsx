/* eslint-disable max-len */
import React from 'react';
import { UncontrolledTooltip } from 'reactstrap';
import { Button } from '../../../../../design';
import { fetchJsonGet, fetchJsonPost } from '../../../../../../utilities/rest';
import {
    convertAuthenticateCredential,
    convertPublicKeyCredentialRequestOptions,
} from '../../../../../../utilities/webauthn';
import { DynamicLayoutContext } from '../../../context';

function WebAuthn() {
    const { ui, callAction } = React.useContext(DynamicLayoutContext);

    const finishAuthenticate = async (publicKeyCredentialCreationOptions) => {
        const createRequest = convertPublicKeyCredentialRequestOptions(publicKeyCredentialCreationOptions);
        const credential = await navigator.credentials.get({ publicKey: createRequest });
        const data = convertAuthenticateCredential(credential, publicKeyCredentialCreationOptions);
        await fetchJsonPost(
            'webauthn/authenticateFinish',
            { data },
            (json) => {
                callAction({ responseAction: json });
            },
        );
    };

    const authenticate = () => {
        fetchJsonGet('webauthn/authenticate',
            {},
            (json) => {
                finishAuthenticate(json);
            });
    };

    return (
        <>
            <Button color="link" onClick={authenticate}>
                <span id="webauthn_authenticate">{ui.translations['webauthn.registration.button.authenticate']}</span>
            </Button>
            <UncontrolledTooltip placement="auto" target="webauthn_authenticate">
                {ui.translations['webauthn.registration.button.authenticate.info']}
            </UncontrolledTooltip>
        </>
    );
}

WebAuthn.propTypes = {};

WebAuthn.defaultProps = {};

export default WebAuthn;