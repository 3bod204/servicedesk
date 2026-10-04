import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { TicketCreate } from './ticket-create';

describe('TicketCreate', () => {
  let component: TicketCreate;
  let fixture: ComponentFixture<TicketCreate>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TicketCreate],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(TicketCreate);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
