import { describe, expect, it } from "vitest";
import { combineDateTimes, splitDateTime } from "./contributionDates";

describe("combineDateTimes", () => {
    it("joint date et heures", () => {
        expect(combineDateTimes("2030-06-21", "20:30", "2030-06-21", "23:00")).toEqual({
            ok: true, startDate: "2030-06-21T20:30", endDate: "2030-06-21T23:00",
        });
    });

    it("garde les dates seules sans heure", () => {
        expect(combineDateTimes("2030-06-21", "", "2030-06-23", "")).toEqual({
            ok: true, startDate: "2030-06-21", endDate: "2030-06-23",
        });
        expect(combineDateTimes("", "", "", "")).toEqual({ ok: true, startDate: undefined, endDate: undefined });
    });

    it("sans date de fin : même jour, ou lendemain si l'heure de fin précède celle du début", () => {
        expect(combineDateTimes("2030-06-21", "14:00", "", "18:00")).toMatchObject({ endDate: "2030-06-21T18:00" });
        expect(combineDateTimes("2030-12-31", "22:00", "", "02:00")).toMatchObject({ endDate: "2031-01-01T02:00" });
    });

    it("refuse une heure sans date de début, ou une fin avant le début", () => {
        expect(combineDateTimes("", "20:00", "", "")).toMatchObject({ ok: false });
        expect(combineDateTimes("2030-06-21", "20:00", "2030-06-21", "18:00")).toMatchObject({ ok: false });
        expect(combineDateTimes("2030-06-21", "", "2030-06-20", "")).toMatchObject({ ok: false });
    });
});

describe("splitDateTime", () => {
    it("sépare date et heure, minuit = pas d'heure", () => {
        expect(splitDateTime("2030-06-21T20:30:00")).toEqual({ date: "2030-06-21", time: "20:30" });
        expect(splitDateTime("2030-06-21T00:00:00")).toEqual({ date: "2030-06-21", time: "" });
        expect(splitDateTime(null)).toEqual({ date: "", time: "" });
    });
});
