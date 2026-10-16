// Les jest.mock ci-dessous sont remontés avant cet import par babel-jest.
import { GoogleSignInError, requestGoogleIdToken, signOutFromGoogle } from '../googleSignIn';

jest.mock('@/config/env', () => ({ googleWebClientId: 'web-id.apps.googleusercontent.com', googleIosClientId: undefined }));
jest.mock('@/config/expoGo', () => ({ isExpoGo: false }));

const mockStatusCodes = { SIGN_IN_CANCELLED: 'CANCELLED', IN_PROGRESS: 'IN_PROGRESS', PLAY_SERVICES_NOT_AVAILABLE: 'NO_PLAY' };
const mockGoogleSignin = {
  configure: jest.fn(),
  hasPlayServices: jest.fn().mockResolvedValue(true),
  signIn: jest.fn(),
  signOut: jest.fn().mockResolvedValue(null),
};
jest.mock('@react-native-google-signin/google-signin', () => ({
  GoogleSignin: mockGoogleSignin,
  statusCodes: mockStatusCodes,
  isSuccessResponse: (response: { type: string }) => response.type === 'success',
  isErrorWithCode: (error: unknown) => typeof error === 'object' && error !== null && 'code' in error,
}));

beforeEach(() => mockGoogleSignin.signIn.mockReset());

it("demande un token destiné au client web et le renvoie", async () => {
  mockGoogleSignin.signIn.mockResolvedValue({ type: 'success', data: { idToken: 'id-token' } });
  await expect(requestGoogleIdToken()).resolves.toBe('id-token');
  expect(mockGoogleSignin.configure).toHaveBeenCalledWith(
    expect.objectContaining({ webClientId: 'web-id.apps.googleusercontent.com' }),
  );
});

it("renvoie null quand l'utilisateur annule", async () => {
  mockGoogleSignin.signIn.mockResolvedValue({ type: 'cancelled', data: null });
  await expect(requestGoogleIdToken()).resolves.toBeNull();
  mockGoogleSignin.signIn.mockRejectedValue({ code: mockStatusCodes.SIGN_IN_CANCELLED });
  await expect(requestGoogleIdToken()).resolves.toBeNull();
});

it('traduit les échecs en messages affichables', async () => {
  mockGoogleSignin.signIn.mockRejectedValue({ code: mockStatusCodes.PLAY_SERVICES_NOT_AVAILABLE });
  await expect(requestGoogleIdToken()).rejects.toThrow(/services Google Play/);
  mockGoogleSignin.signIn.mockRejectedValue(new Error('DEVELOPER_ERROR'));
  await expect(requestGoogleIdToken()).rejects.toBeInstanceOf(GoogleSignInError);
  mockGoogleSignin.signIn.mockResolvedValue({ type: 'success', data: { idToken: null } });
  await expect(requestGoogleIdToken()).rejects.toThrow(/identifiant/);
});

it('se déconnecte de Google à la déconnexion', async () => {
  await signOutFromGoogle();
  expect(mockGoogleSignin.signOut).toHaveBeenCalled();
});
